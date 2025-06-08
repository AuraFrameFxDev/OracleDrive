// In OracleDrive - Genesisystem/app/src/main/java/com/genesis/ai/app/data/logging/OracleDriveLogger.kt
package com.genesis.ai.app.data.logging

import android.content.Context
import android.util.Log // Android's Log for fallback or internal logging of the logger itself
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // For switching context for file I/O
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.StandardCharsets

// The issue mentions @Singleton and @Inject, implying Hilt.
// However, the plan for GenesisApplication shows direct instantiation.
// For this step, creating it as a regular class that can be directly instantiated.
// If Hilt is indeed used in the project, this can be annotated later.
class OracleDriveLogger(
    private val context: Context // Application context for file access
) {
    private val TAG = "OracleDriveLoggerInternal" // Renamed to avoid conflict if used in class being logged
    private val LOG_FILENAME_PREFIX = "oracledrive_log_"
    private val LOG_DIR = "od_logs" // Dedicated log subdirectory in app's internal files directory
    private val LOG_RETENTION_DAYS = 3 // Shorter retention for device logs

    // Using SupervisorJob so if one log operation fails, it doesn't cancel the whole scope
    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Log.d(TAG, "OracleDriveLogger initialized. Starting log cleanup.")
        loggerScope.launch {
            cleanupOldLogs() // Run cleanup on init
        }
    }

    private fun getCurrentLogFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return "$LOG_FILENAME_PREFIX${dateFormat.format(Date())}.txt"
    }

    // Changed to suspend fun as it performs I/O
    private suspend fun writeLogEntry(level: String, logTag: String, message: String, throwable: Throwable? = null) {
        // Ensure all file operations are on an I/O dispatcher
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val logEntry = "[$timestamp][$level/$logTag] $message"
            val fullLogEntry = if (throwable != null) {
                "$logEntry
${Log.getStackTraceString(throwable)}" // Using Android's Log util for stack trace
            } else {
                logEntry
            }

            val logDirFile = File(context.filesDir, LOG_DIR)
            if (!logDirFile.exists()) {
                if (!logDirFile.mkdirs()) {
                    Log.e(TAG, "Failed to create log directory: ${logDirFile.absolutePath}")
                    return@withContext
                }
            }

            val filePath = File(logDirFile, getCurrentLogFileName())

            try {
                FileOutputStream(filePath, true).use { fos -> // Append mode
                    fos.write(fullLogEntry.toByteArray(StandardCharsets.UTF_8))
                    fos.write("
".toByteArray(StandardCharsets.UTF_8)) // New line for each entry
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing log to file '${filePath.absolutePath}': ${e.message}", e)
            }
        }
    }

    // Public logging methods launch a coroutine in loggerScope
    fun d(tag: String, message: String, throwable: Throwable? = null) = loggerScope.launch { writeLogEntry("DEBUG", tag, message, throwable) }
    fun i(tag: String, message: String, throwable: Throwable? = null) = loggerScope.launch { writeLogEntry("INFO", tag, message, throwable) }
    fun w(tag: String, message: String, throwable: Throwable? = null) = loggerScope.launch { writeLogEntry("WARN", tag, message, throwable) }
    // Corrected: removed extra {} from the e function in the prompt
    fun e(tag: String, message: String, throwable: Throwable? = null) = loggerScope.launch { writeLogEntry("ERROR", tag, message, throwable) }
    fun v(tag: String, message: String, throwable: Throwable? = null) = loggerScope.launch { writeLogEntry("VERBOSE", tag, message, throwable) }

    // Changed to suspend fun as it performs I/O
    suspend fun readCurrentDayLogs(): String {
        // Ensure all file operations are on an I/O dispatcher
        return withContext(Dispatchers.IO) {
            val fileName = getCurrentLogFileName()
            val logDirFile = File(context.filesDir, LOG_DIR)
            val filePath = File(logDirFile, fileName)
            try {
                if (filePath.exists()) {
                    FileInputStream(filePath).use { fis ->
                        fis.readBytes().toString(StandardCharsets.UTF_8)
                    }
                } else {
                    "Log file for today (${filePath.absolutePath}) does not exist."
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading log file '${filePath.absolutePath}': ${e.message}", e)
                "Error reading logs: ${e.message}"
            }
        }
    }

    // Changed to suspend fun as it performs I/O
    private suspend fun cleanupOldLogs() {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val logDirFile = File(context.filesDir, LOG_DIR)

            if (logDirFile.exists() && logDirFile.isDirectory) {
                logDirFile.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(LOG_FILENAME_PREFIX)) {
                        val lastModified = file.lastModified()
                        if (lastModified < cutoffTime) {
                            val deleted = file.delete()
                            Log.d(TAG, "Cleaned up old log file: ${file.name} (deleted: $deleted)")
                        }
                    }
                }
            } else {
                Log.d(TAG, "Log directory not found for cleanup: ${logDirFile.absolutePath}")
            }
        }
    }

    fun shutdown() {
        Log.d(TAG, "OracleDriveLogger shutting down.")
        // Cancel all coroutines started by this scope.
        // Waits for active jobs to complete if they are non-cancellable,
        // but file I/O operations with `withContext(Dispatchers.IO)` should be cancellable.
        loggerScope.cancel()
    }
}
