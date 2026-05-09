package com.android.tv.classics.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.android.tv.classics.BuildConfig
import com.android.tv.classics.LiveTvApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages app updates by checking GitHub Releases on a private repository.
 * Compares the latest release tag against the current versionName and downloads
 * the matching APK asset via DownloadManager with Bearer auth headers.
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_FOLDER = "JasmineTvUpdates"
        private const val APK_NAME = "JasmineTv-update.apk"

        private var downloadId: Long = -1L
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
            if (id == downloadId) {
                Timber.d("Download complete, launching installer")
                installDownloadedApk()
            }
        }
    }

    fun initialize() {
        context.registerReceiver(
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    fun destroy() {
        try { context.unregisterReceiver(downloadCompleteReceiver) } catch (e: Exception) { /* already unregistered */ }
    }

    // ───────────────────────── Public API ─────────────────────────

    /**
     * Fetches the latest GitHub Release and returns an [UpdateInfo].
     * Runs on Dispatchers.IO — call from a coroutine.
     */
    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val json = fetchLatestRelease()
            if (json.isEmpty()) return@withContext UpdateInfo(isUpdateAvailable = false)

            val release = JSONObject(json)
            val tagName = release.optString("tag_name", "")          // e.g. "v1.2.0"
            val releaseName = release.optString("name", tagName)
            val releaseNotes = release.optString("body", "")
            val publishedAt = release.optString("published_at", "")

            // Strip leading "v" for version comparison
            val remoteVersion = tagName.trimStart('v')

            // Find the APK asset
            val assets = release.optJSONArray("assets") ?: JSONArray()
            val asset = findApkAsset(assets)

            UpdateInfo(
                isUpdateAvailable = isNewer(remoteVersion, BuildConfig.VERSION_NAME),
                tagName = tagName,
                versionName = remoteVersion,
                releaseName = releaseName,
                downloadApiUrl = asset?.optString("url"),       // API URL (needs auth)
                assetId = asset?.optLong("id") ?: -1L,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            UpdateInfo(isUpdateAvailable = false)
        }
    }

    /**
     * Enqueues an authenticated download via [DownloadManager].
     * Returns true if the download was successfully enqueued.
     */
    fun startUpdateDownload(updateInfo: UpdateInfo): Boolean {
        val apiUrl = updateInfo.downloadApiUrl ?: return false
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // For private GitHub repos the asset must be fetched via the API URL
            // with Accept: application/octet-stream + Authorization header.
            val request = DownloadManager.Request(Uri.parse(apiUrl)).apply {
                setTitle("Jasmine TV Update")
                setDescription("Downloading ${updateInfo.releaseName}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                addRequestHeader("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN}")
                addRequestHeader("Accept", "application/octet-stream")
                addRequestHeader("X-GitHub-Api-Version", "2022-11-28")

                val destFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    APK_NAME
                )
                setDestinationUri(Uri.fromFile(destFile))
                setMimeType(APK_MIME_TYPE)
            }

            downloadId = dm.enqueue(request)
            Timber.d("Download enqueued id=$downloadId  url=$apiUrl")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error starting download")
            false
        }
    }

    fun getDownloadProgress(): Int {
        if (downloadId == -1L) return -1
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor.moveToFirst()) {
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) return ((downloaded * 100) / total).toInt()
            }
        }
        return -1
    }

    fun cancelDownload() {
        if (downloadId != -1L) {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
            downloadId = -1L
        }
    }

    // ───────────────────────── Private helpers ─────────────────────────

    private suspend fun fetchLatestRelease(): String = withContext(Dispatchers.IO) {
        val url = URL("$GITHUB_API_BASE/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.GITHUB_TOKEN}")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.connect()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                Timber.e("GitHub API returned ${conn.responseCode}")
                ""
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Finds the APK asset matching [BuildConfig.GITHUB_APK_ASSET_NAME]. */
    private fun findApkAsset(assets: JSONArray): JSONObject? {
        val targetName = BuildConfig.GITHUB_APK_ASSET_NAME
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == targetName ||
                asset.optString("content_type") == APK_MIME_TYPE) {
                return asset
            }
        }
        // Fallback: any .apk file
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk")) return asset
        }
        return null
    }

    /**
     * Compare version strings like "1.2.3". Returns true if [remote] > [current].
     * Falls back to string comparison if parsing fails.
     */
    private fun isNewer(remote: String, current: String): Boolean {
        return try {
            val r = remote.split(".").map { it.toInt() }
            val c = current.split(".").map { it.toInt() }
            val len = maxOf(r.size, c.size)
            for (i in 0 until len) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            false
        } catch (e: Exception) {
            remote != current
        }
    }

    private fun installDownloadedApk() {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) return
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val file = File(Uri.parse(localUri).path ?: return)
                installApk(file)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error triggering install")
            LiveTvApplication.showToast("Error installing update")
        }
    }

    private fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(apkFile), APK_MIME_TYPE)
            }
        }
        context.startActivity(intent)
    }

    // ───────────────────────── Data ─────────────────────────

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val tagName: String = "",
        val versionName: String = "",
        val releaseName: String = "",
        val downloadApiUrl: String? = null,
        val assetId: Long = -1L,
        val releaseNotes: String = "",
        val publishedAt: String = ""
    ) : java.io.Serializable
}
