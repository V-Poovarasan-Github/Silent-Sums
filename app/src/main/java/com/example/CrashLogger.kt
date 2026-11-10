package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File

object CrashLogger {
    const val LOG_PATH_1 = "logs/last_crash.log"
    const val LOG_PATH_2 = "crash_logs/latest_crash.txt"

    fun getLogFile1(context: Context): File = File(context.filesDir, LOG_PATH_1)
    fun getLogFile2(context: Context): File = File(context.filesDir, LOG_PATH_2)

    /**
     * Checks if a log string contains an actual stack trace rather than just the clean
     * "APP START" banner.
     */
    fun hasStackTrace(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val trimmed = content.trim()
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false

        // If all non-empty lines are APP START lines, this is a clean start without crashes
        if (lines.all { it.startsWith("APP START") }) return false

        // Check for stack trace indicators or exception lines
        val hasTraceKeyword = lines.any { line ->
            line.contains("Exception") ||
            line.contains("Error") ||
            line.contains("STACK TRACE") ||
            line.contains("CRASH REPORT") ||
            line.contains("at ") ||
            line.startsWith("\tat ") ||
            line.contains(".kt:") ||
            line.contains(".java:")
        }
        return hasTraceKeyword || lines.size > 1
    }

    /**
     * Inspects both private crash log paths in app storage.
     * Returns the text of the most recent crash containing a stack trace, or null if clean.
     */
    fun checkCrashLog(context: Context): String? {
        val file1 = getLogFile1(context)
        val file2 = getLogFile2(context)

        val log1 = if (file1.exists()) try { file1.readText() } catch (_: Exception) { null } else null
        val log2 = if (file2.exists()) try { file2.readText() } catch (_: Exception) { null } else null

        val has1 = hasStackTrace(log1)
        val has2 = hasStackTrace(log2)

        return when {
            has1 && has2 -> {
                if (file1.lastModified() >= file2.lastModified()) log1 else log2
            }
            has1 -> log1
            has2 -> log2
            else -> null
        }
    }

    /**
     * Clears all crash logs from internal storage.
     */
    fun clearCrashLogs(context: Context) {
        try { getLogFile1(context).delete() } catch (_: Exception) {}
        try { getLogFile2(context).delete() } catch (_: Exception) {}
    }

    /**
     * Single source of truth for the formatted version + build string.
     */
    fun versionBadge(): String =
        "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"

    /**
     * Builds the crash-log header line including the version badge and timestamp.
     */
    fun buildStartHeader(timestamp: String = "${System.currentTimeMillis()}"): String =
        "APP START ${versionBadge()} $timestamp\n"

    /**
     * Records a clean start marker without stack trace.
     */
    fun recordCleanStart(context: Context) {
        val header = buildStartHeader()
        try {
            val file1 = getLogFile1(context)
            file1.parentFile?.mkdirs()
            file1.writeText(header)
        } catch (_: Exception) {}
        try {
            val file2 = getLogFile2(context)
            file2.parentFile?.mkdirs()
            file2.writeText(header)
        } catch (_: Exception) {}
    }

    /**
     * Creates the Android ACTION_SEND Intent with plain text type and crash report payload.
     */
    fun createShareIntent(crashLogText: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SilentSums Crash Report")
            putExtra(Intent.EXTRA_TEXT, crashLogText)
        }
    }

    /**
     * Launches the system share chooser for the crash log.
     */
    fun shareCrashLog(activity: Activity, crashLogText: String) {
        val intent = createShareIntent(crashLogText)
        val chooser = Intent.createChooser(intent, "SilentSums Crash Report")
        activity.startActivity(chooser)
    }
}
