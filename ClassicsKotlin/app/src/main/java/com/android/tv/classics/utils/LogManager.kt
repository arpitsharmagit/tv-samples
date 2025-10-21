package com.android.tv.classics.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.BufferedReader
import java.io.FileReader

object LogManager {
    private const val MAX_LOG_LINES = 100 // Limit to latest 100 records
    private const val DEFAULT_LOG_LEVEL = "ERROR" // Default to ERROR level
    
    fun clearLogs() {
        val context = getApplicationContext()
        val logDir = File(context.getExternalFilesDir(null), "logs")
        
        if (!logDir.exists()) return
        
        // Delete all log files
        val logFiles = (listOf(File(logDir, "app.log")) + 
                      (1..4).map { File(logDir, "app.log.$it") })
                     .filter { it.exists() }
        
        logFiles.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete log file: ${file.name}")
            }
        }
    }

    fun readLogs(logLevel: String = "ALL"): List<String> {
        val logLines = mutableListOf<String>()
        val context = getApplicationContext()
        val logDir = File(context.getExternalFilesDir(null), "logs")
        
        if (!logDir.exists()) return emptyList()

        // Always include crash logs if they exist when ERROR level is selected
        if (logLevel == "ERROR") {
            logLines.addAll(CrashLogger.getCrashLogs(context))
        }
        
        // Read all log files in reverse order (newest first)
        val logFiles = (listOf(File(logDir, "app.log")) + 
                      (1..4).map { File(logDir, "app.log.$it") })
                     .filter { it.exists() }

        var linesRead = 0
        for (file in logFiles) {
            try {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lines().forEach { line ->
                        if (linesRead >= MAX_LOG_LINES) return@forEach
                        
                        // Filter by log level if specified
                        if (logLevel != "ALL") {
                            val levelChar = when (logLevel) {
                                "VERBOSE" -> 'V'
                                "DEBUG" -> 'D'
                                "INFO" -> 'I'
                                "WARN" -> 'W'
                                "ERROR" -> 'E'
                                else -> null
                            }
                            
                            // Match log level based on Android log format: "YYYY-MM-DD HH:mm:ss.SSS L/"
                            // This looks for the level character after the timestamp
                            val timestampLength = "YYYY-MM-DD HH:mm:ss.SSS ".length
                            if (levelChar != null && 
                                (line.length < timestampLength + 2 || 
                                 line[timestampLength] != levelChar || 
                                 line[timestampLength + 1] != '/')) {
                                return@forEach
                            }
                        }
                        
                        logLines.add(line)
                        linesRead++
                    }
                }
            } catch (e: Exception) {
                Timber.e("LogManager", "Error reading log file: ${file.name}", e)
            }
        }

        return logLines.reversed() // Show newest logs first
    }

    fun clearLogs(context: Context) {
        FileLoggingTree.cleanAllLogs(context)
    }

    private fun getApplicationContext(): Context {
        return ApplicationContextProvider.applicationContext
            ?: throw IllegalStateException("Application context not available")
    }
}