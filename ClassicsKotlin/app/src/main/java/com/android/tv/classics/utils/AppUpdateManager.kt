package com.android.tv.classics.utils

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.tv.classics.BuildConfig
import com.android.tv.classics.LiveTvApplication
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection

/**
 * Manages app updates from a custom URL (not Google Play Store)
 */
class AppUpdateManager(private val context: Context) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val UPDATE_INFO_URL = "https://drive.google.com/uc?export=download&id=1ZmNlDblW5z1fV18yW_FYlUX3P2hBCO2F"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        
        // File paths and names
        private const val DOWNLOAD_FOLDER = "JasmineTvUpdates"
        private const val APK_NAME = "JasmineTv-update.apk"
        
        // Used to store the download ID from DownloadManager
        private var downloadId: Long = -1
    }
    
    // BroadcastReceiver to handle download completion
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                Timber.d("Download complete, installing update")
                installDownloadedApk()
            }
        }
    }
    
    /**
     * Initialize the update manager and register receivers
     */
    fun initialize() {
        // Register for download complete broadcasts
        context.registerReceiver(
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        try {
            context.unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            Timber.e( "Error unregistering receiver", e)
        }
    }
    
    /**
     * Check for app updates
     * @return true if an update is available
     */
    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            // Get current app version
            val currentVersion = getCurrentAppVersion()
            
            // Fetch update information from remote server
            val updateJson = fetchUpdateInfo()
            val updateInfo = parseUpdateInfo(updateJson)
            
            // Compare versions
            val isUpdateAvailable = isUpdateAvailable(currentVersion, updateInfo.versionCode)
            
            return@withContext UpdateInfo(
                isUpdateAvailable = isUpdateAvailable,
                versionName = updateInfo.versionName,
                versionCode = updateInfo.versionCode,
                downloadUrl = updateInfo.downloadUrl,
                releaseNotes = updateInfo.releaseNotes,
                updateDate = updateInfo.updateDate,
                forceUpdate = updateInfo.forceUpdate,
                minSupportedVersion = updateInfo.minSupportedVersion
            )
        } catch (e: Exception) {
            Timber.e( "Error checking for updates", e)
            return@withContext UpdateInfo(isUpdateAvailable = false)
        }
    }
    
    /**
     * Start downloading the update
     * @param updateInfo Information about the update to download
     * @return true if download started successfully
     */
    fun startUpdateDownload(updateInfo: UpdateInfo): Boolean {
        try {
            val downloadUrl = updateInfo.downloadUrl ?: return false
            
            // Check if we have permission to write to external storage
            val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                        PackageManager.PERMISSION_GRANTED
            } else {
                true // Permission is granted at install time on older Android versions
            }
            
            // Create download request
            val uri = Uri.parse(downloadUrl)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri).apply {
                setTitle("Jasmine TV Update")
                setDescription("Downloading version ${updateInfo.versionName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                if (hasStoragePermission) {
                    // Create download directory if it doesn't exist
                    val downloadFolder = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        DOWNLOAD_FOLDER
                    )
                    if (!downloadFolder.exists()) {
                        downloadFolder.mkdirs()
                    }
                    
                    // Save to public external storage
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "$DOWNLOAD_FOLDER/$APK_NAME"
                    )
                } else {
                    // No permission, use app's private directory
                    // This will be cleaned up when the app is uninstalled
                    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)
                    setDestinationUri(Uri.fromFile(file))
                    Timber.d("Using app-specific storage for download: ${file.absolutePath}")
                }
                
                setMimeType(APK_MIME_TYPE)
            }
            
            // Start download
            downloadId = downloadManager.enqueue(request)
            return true
        } catch (e: Exception) {
            Timber.e( "Error starting download", e)
            return false
        }
    }
    
    /**
     * Get download progress
     * @return progress percentage (0-100) or -1 if not found
     */
    fun getDownloadProgress(): Int {
        if (downloadId == -1L) return -1
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val bytesTotal = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                
                if (bytesTotal > 0) {
                    return ((bytesDownloaded * 100) / bytesTotal).toInt()
                }
            }
        }
        
        return -1
    }
    
    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1L
        }
    }
    
    /**
     * Install the downloaded APK
     */
    private fun installDownloadedApk() {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            
            downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (columnIndex != -1 && 
                        cursor.getInt(columnIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex != -1) {
                            val uriString = cursor.getString(uriIndex)
                            val uri = Uri.parse(uriString)
                            val file = File(uri.path ?: "")
                            
                            installApk(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e( "Error installing APK", e)
            LiveTvApplication.showToast("Error installing update: ${e.message}")
        }
    }
    
    /**
     * Install APK file
     */
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri: Uri
            
            // For Android N and above, we need to use FileProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                uri = Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(uri, APK_MIME_TYPE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e( "Error launching install intent", e)
            LiveTvApplication.showToast("Error installing update: ${e.message}")
        }
    }
    
    /**
     * Get current app version code
     */
    private fun getCurrentAppVersion(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: Exception) {
            Timber.e( "Error getting current app version", e)
            BuildConfig.VERSION_CODE
        }
    }
    
    /**
     * Fetch update information from remote server
     */
    private suspend fun fetchUpdateInfo(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_INFO_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                return@withContext response.toString()
            } else {
                Timber.e( "Server returned error code: ${connection.responseCode}")
                return@withContext ""
            }
        } catch (e: Exception) {
            Timber.e( "Error fetching update info", e)
            return@withContext ""
        }
    }
    
    /**
     * Parse update information from JSON
     */
    private fun parseUpdateInfo(jsonString: String): UpdateInfo {
        return try {
            if (jsonString.isEmpty()) {
                return UpdateInfo(isUpdateAvailable = false)
            }
            
            val json = JSONObject(jsonString)
            
            UpdateInfo(
                isUpdateAvailable = false, // Will be determined later by comparing versions
                versionName = json.optString("versionName", ""),
                versionCode = json.optInt("versionCode", 0),
                downloadUrl = json.optString("downloadUrl", ""),
                releaseNotes = json.optString("releaseNotes", ""),
                updateDate = json.optString("updateDate", ""),
                forceUpdate = json.optBoolean("forceUpdate", false),
                minSupportedVersion = json.optInt("minSupportedVersion", 0)
            )
        } catch (e: Exception) {
            Timber.e( "Error parsing update info", e)
            UpdateInfo(isUpdateAvailable = false)
        }
    }
    
    /**
     * Check if an update is available by comparing version codes
     */
    private fun isUpdateAvailable(currentVersion: Int, newVersion: Int): Boolean {
        return newVersion > currentVersion
    }
    
    /**
     * Data class representing update information
     */
    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val versionName: String = "",
        val versionCode: Int = 0,
        val downloadUrl: String? = null,
        val releaseNotes: String = "",
        val updateDate: String = "",
        val forceUpdate: Boolean = false,
        val minSupportedVersion: Int = 0
    ) : java.io.Serializable
}