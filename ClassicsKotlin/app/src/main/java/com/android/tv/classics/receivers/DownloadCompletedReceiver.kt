package com.android.tv.classics.receivers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * BroadcastReceiver to handle APK download completion and prompt installation
 */
class DownloadCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DownloadReceiver"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_FOLDER = "JasmineTvUpdates"
        private const val APK_NAME = "JasmineTv-update.apk"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                
                if (downloadId != -1L) {
                    // Check if this is our APK download
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    
                    downloadManager.query(query).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                // If file column exists, use it
                                val fileUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                val filePathIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                                
                                if (fileUriIdx != -1) {
                                    val fileUri = cursor.getString(fileUriIdx)
                                    val file = File(Uri.parse(fileUri).path ?: "")
                                    
                                    if (file.exists() && file.path.contains(DOWNLOAD_FOLDER)) {
                                        installApk(context, file)
                                    }
                                } else if (filePathIdx != -1) {
                                    // Fallback for older Android versions
                                    val filePath = cursor.getString(filePathIdx)
                                    val file = File(filePath)
                                    
                                    if (file.exists() && file.path.contains(DOWNLOAD_FOLDER)) {
                                        installApk(context, file)
                                    }
                                } else {
                                    // Last resort - construct the file path manually
                                    val downloadFolder = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        DOWNLOAD_FOLDER
                                    )
                                    val apkFile = File(downloadFolder, APK_NAME)
                                    
                                    if (apkFile.exists()) {
                                        installApk(context, apkFile)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling download completion", e)
        }
    }
    
    /**
     * Install the downloaded APK
     */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // For Android 7.0+ (API 24+), we need to use FileProvider
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        apkFile
                    )
                    setDataAndType(uri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    // For older versions, we can use file:// URIs
                    setDataAndType(Uri.fromFile(apkFile), APK_MIME_TYPE)
                }
            }
            
            context.startActivity(intent)
            Log.d(TAG, "APK installation intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting APK installation", e)
        }
    }
}