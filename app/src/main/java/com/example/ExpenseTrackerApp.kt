package com.example

import android.app.Application
import android.util.Log
import com.example.importbackup.MediaStoreWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val startHeader = CrashLogger.buildStartHeader(timestamp)

        // 1. Overwrite crash.log fresh on each app start before anything else runs
        try {
            MediaStoreWriter.writeCrashLog(this, startHeader)
        } catch (e: Throwable) {
            Log.e("ExpenseTrackerApp", "Failed to write initial crash.log", e)
        }

        // 2. Install global uncaught-exception handler
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()
                val stackTrace = sw.toString()

                val crashLog = StringBuilder()
                    .append("\n================ CRASH REPORT ================\n")
                    .append("TIMESTAMP: ").append(crashTimestamp).append("\n")
                    .append("THREAD: ").append(thread.name).append(" (id: ").append(thread.id).append(")\n")
                    .append("EXCEPTION: ").append(throwable.javaClass.name).append("\n")
                    .append("MESSAGE: ").append(throwable.message ?: "null").append("\n")
                    .append("STACK TRACE:\n").append(stackTrace)
                    .append("==============================================\n")
                    .toString()

                val fullLog = startHeader + crashLog
                MediaStoreWriter.writeCrashLog(this, fullLog)
            } catch (loggingError: Throwable) {
                Log.e("ExpenseTrackerApp", "Failed to write crash to file", loggingError)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
