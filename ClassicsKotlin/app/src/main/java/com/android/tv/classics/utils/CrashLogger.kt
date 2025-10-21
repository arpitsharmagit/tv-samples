package com.android.tv.classics.utils

import android.content.Context
import android.os.Process
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val crashLogFile = "crash.log"

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Get the stack trace
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            
            // Create crash report
            val report = StringBuilder()
            report.append("************ CRASH REPORT ************\n")
            report.append("Time: ${dateFormat.format(Date())}\n")
            report.append("Thread: ${thread.name}\n")
            report.append("Exception: ${throwable.javaClass.name}\n")
            report.append("Message: ${throwable.message}\n")
            report.append("Stack Trace:\n")
            report.append(stackTrace.toString())
            report.append("\n")
            report.append("Device Info:\n")
            report.append("Brand: ${android.os.Build.BRAND}\n")
            report.append("Device: ${android.os.Build.DEVICE}\n")
            report.append("Model: ${android.os.Build.MODEL}\n")
            report.append("Android Version: ${android.os.Build.VERSION.RELEASE}\n")
            report.append("App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}\n")
            report.append("************ END CRASH REPORT ************\n\n")

            // Write to crash log file
            val crashDir = File(context.getExternalFilesDir(null), "logs")
            crashDir.mkdirs()
            val crashFile = File(crashDir, crashLogFile)
            
            // Append the crash report to the file
            FileWriter(crashFile, true).use { writer ->
                writer.append(report)
            }

            // Log to Timber as well
            Timber.e(throwable, "App Crashed: ${throwable.message}")

        } catch (e: Exception) {
            // If anything fails, make sure we at least log it
            Timber.e(e, "Error writing crash report")
        } finally {
            // Make sure the default handler runs to properly terminate the app
            defaultHandler?.uncaughtException(thread, throwable)
            
            // If there's no default handler, terminate the process
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }

    companion object {
        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashLogger(context))
        }
        
        fun getCrashLogs(context: Context): List<String> {
            val crashFile = File(File(context.getExternalFilesDir(null), "logs"), "crash.log")
            if (!crashFile.exists()) return emptyList()
            
            return try {
                crashFile.readLines().reversed()
            } catch (e: Exception) {
                Timber.e(e, "Error reading crash logs")
                emptyList()
            }
        }
        
        fun clearCrashLogs(context: Context) {
            val crashFile = File(File(context.getExternalFilesDir(null), "logs"), "crash.log")
            if (crashFile.exists()) {
                crashFile.delete()
            }
        }
    }
}