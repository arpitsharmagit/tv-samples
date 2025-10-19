package com.android.tv.classics.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileLoggingTree(context: Context) : Timber.Tree() {
    private val logDir: File = File(context.getExternalFilesDir(null), "logs")
    private val maxFileSize = 2L * 1024 * 1024 // 2MB
    private val maxFiles = 5
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = ReentrantLock()
    
    init {
        logDir.mkdirs()
        cleanupOldLogs()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val logFile = getCurrentLogFile()
        val priorityChar = when (priority) {
            Log.VERBOSE -> 'V'
            Log.DEBUG -> 'D'
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR -> 'E'
            Log.ASSERT -> 'A'
            else -> 'U'
        }

        val timestamp = dateFormat.format(Date())
        val logMessage = "$timestamp $priorityChar/$tag: $message\n"
        t?.let { logMessage + "${Log.getStackTraceString(it)}\n" }

        lock.withLock {
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append(logMessage)
                }

                // Check file size and rotate if necessary
                if (logFile.length() > maxFileSize) {
                    rotateLogFiles()
                }
            } catch (e: Exception) {
                Timber.e("FileLoggingTree", "Failed to write log", e)
            }
        }
    }

    private fun getCurrentLogFile(): File {
        return File(logDir, "app.log")
    }

    private fun rotateLogFiles() {
        // Delete the oldest log file if we have reached max files
        val oldestLog = File(logDir, "app.log.${maxFiles - 1}")
        if (oldestLog.exists()) {
            oldestLog.delete()
        }

        // Shift all existing log files
        for (i in (maxFiles - 2) downTo 0) {
            val file = File(logDir, if (i == 0) "app.log" else "app.log.$i")
            val nextFile = File(logDir, "app.log.${i + 1}")
            if (file.exists()) {
                file.renameTo(nextFile)
            }
        }

        // Create new empty log file
        getCurrentLogFile().createNewFile()
    }

    private fun cleanupOldLogs() {
        logDir.listFiles()?.forEach { file ->
            try {
                val match = Regex("""app\.log\.?(\d*)""").find(file.name)
                if (match != null) {
                    val number = match.groupValues[1].toIntOrNull() ?: 0
                    if (number >= maxFiles) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Timber.e("FileLoggingTree", "Failed to cleanup log file: ${file.name}", e)
            }
        }
    }

    companion object {
        fun cleanAllLogs(context: Context) {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (logDir.exists()) {
                logDir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}