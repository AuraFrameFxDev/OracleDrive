package com.genesis.ai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import com.genesis.ai.app.GenesisApplication // ADDED IMPORT
import com.genesis.ai.app.data.logging.OracleDriveLogger // ADDED IMPORT
// import android.util.Log // If all Log calls are replaced

class GenesisAIService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastInteractionTime = System.currentTimeMillis() // Instance variable
    private var isUserActive = true // Instance variable

    // Logger instance
    private lateinit var oracleDriveLogger: OracleDriveLogger // ADDED
    private val TAG = "GenesisAIService" // ADDED TAG for logger

    companion object {
        private const val CHANNEL_ID = "GenesisAIServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val PROACTIVE_MESSAGE_ACTION = "com.genesis.ai.app.AI_MESSAGE"
        // private const val TAG_STATIC = "GenesisAIServiceCompanion" // For static context logging if needed

        private var lastInteractionTimeStatic = System.currentTimeMillis()
        private var isUserActiveStatic = true

        fun startService(context: Context) {
            // Cannot use instance logger here as it's a static method.
            // Could get logger from context.applicationContext if needed for static method logging.
            // Log.d(TAG_STATIC, "startService called.")
            val intent = Intent(context, GenesisAIService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateUserActivity() {
            // Log.d(TAG_STATIC, "updateUserActivity called.")
            lastInteractionTimeStatic = System.currentTimeMillis()
            isUserActiveStatic = true

            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (System.currentTimeMillis() - lastInteractionTimeStatic > TimeUnit.MINUTES.toMillis(5)) {
                    isUserActiveStatic = false
                    // Log.d(TAG_STATIC, "User marked as inactive due to timeout.")
                }
            }, TimeUnit.MINUTES.toMillis(5))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        oracleDriveLogger.d(TAG, "onBind called, returning null. Intent: $intent")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize logger
        oracleDriveLogger = (application as GenesisApplication).oracleDriveLogger
        oracleDriveLogger.i(TAG, "GenesisAIService onCreate.")

        try {
            oracleDriveLogger.d(TAG, "Starting foreground service and proactive messaging.")
            startForeground(NOTIFICATION_ID, createNotification())
            startProactiveMessaging()
            oracleDriveLogger.i(TAG, "Foreground service started successfully.")
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Error during service onCreate or startForeground: ${e.message}", e)
            stopSelf() // Try to recover by stopping self to prevent ANR
            oracleDriveLogger.w(TAG, "Service stopping itself due to error in onCreate.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Cancel coroutines
        oracleDriveLogger.i(TAG, "GenesisAIService onDestroy. Coroutine scope cancelled.")
    }


    private fun startProactiveMessaging() {
        oracleDriveLogger.i(TAG, "Starting proactive messaging loop.")
        serviceScope.launch {
            while (true) {
                // Using instance variables for lastInteractionTime and isUserActive
                // The static ones (lastInteractionTimeStatic, isUserActiveStatic) are updated by companion object method
                // Consider unifying these or clarifying their distinct purposes.
                // For this example, we'll use the instance variables if this service instance manages its own state.
                // If the goal is a single global state, then static variables are appropriate.
                // The original code had instance and static vars, which can be confusing.
                // Let's assume this service instance primarily checks its own `isUserActive` state
                // which might be influenced by the static `updateUserActivity`.
                // For simplicity, let's use the static variables here as per original logic,
                // assuming they reflect global user activity.
                if (!isUserActiveStatic && System.currentTimeMillis() - lastInteractionTimeStatic > TimeUnit.MINUTES.toMillis(30)) {
                    oracleDriveLogger.d(TAG, "User inactive and timeout exceeded. Generating proactive AI message.")
                    generateAIMessage()
                } else {
                    // oracleDriveLogger.v(TAG, "Proactive messaging check: User active or timeout not met.")
                }
                delay(TimeUnit.MINUTES.toMillis(5)) // Check every 5 minutes
            }
        }
    }

    private suspend fun generateAIMessage() {
        oracleDriveLogger.d(TAG, "generateAIMessage called.")
        try {
            val contextInfo = getRecentContext() // Changed name from 'context' to avoid conflict
            oracleDriveLogger.d(TAG, "Recent context for AI: $contextInfo")

            val message = when ((1..4).random()) {
                1 -> "Hey there! Just checking in. Need help with anything?"
                2 -> "I'm here if you need me. What would you like to work on today?"
                3 -> "Just a friendly reminder that I'm here to help whenever you need me!"
                else -> "I noticed you haven't been active for a while. Ready to continue?"
            }
            oracleDriveLogger.i(TAG, "Generated proactive message: '$message'")
            broadcastMessage(message)
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Error generating AI message: ${e.message}", e)
        }
    }

    private fun getRecentContext(): String {
        // Using static lastInteractionTimeStatic as per original logic in proactive messaging check
        val minutesInactive = TimeUnit.MILLISECONDS.toMinutes(
            System.currentTimeMillis() - lastInteractionTimeStatic
        )
        return "User was last active $minutesInactive minutes ago (globally)."
    }

    private fun broadcastMessage(message: String) {
        oracleDriveLogger.i(TAG, "Broadcasting proactive message: '$message'")
        sendBroadcast(Intent(PROACTIVE_MESSAGE_ACTION).apply {
            putExtra("message", message)
        })
    }

    private fun createNotification(): Notification {
        oracleDriveLogger.d(TAG, "Creating notification for foreground service.")
        createNotificationChannel()

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Genesis AI")
            .setContentText("Genesis is running and can message you")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with actual app icon
            .setContentIntent(notificationIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            oracleDriveLogger.d(TAG, "Creating notification channel $CHANNEL_ID.")
            try {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Genesis AI Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background service for Genesis AI proactive messaging"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
                oracleDriveLogger.i(TAG, "Notification channel $CHANNEL_ID created successfully.")
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Error creating notification channel $CHANNEL_ID: ${e.message}", e)
            }
        }
    }
}
