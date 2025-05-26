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

class GenesisAIService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastInteractionTime = System.currentTimeMillis()
    private var isUserActive = true

    companion object {
        private const val CHANNEL_ID = "GenesisAIServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val PROACTIVE_MESSAGE_ACTION = "com.genesis.ai.app.AI_MESSAGE"

        private var lastInteractionTimeStatic = System.currentTimeMillis()
        private var isUserActiveStatic = true

        fun startService(context: Context) {
            val intent = Intent(context, GenesisAIService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateUserActivity() {
            lastInteractionTimeStatic = System.currentTimeMillis()
            isUserActiveStatic = true

            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (System.currentTimeMillis() - lastInteractionTimeStatic > TimeUnit.MINUTES.toMillis(
                        5
                    )
                ) {
                    isUserActiveStatic = false
                }
            }, TimeUnit.MINUTES.toMillis(5))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            startProactiveMessaging()
        } catch (e: Exception) {
            e.printStackTrace()
            // Try to recover by stopping self to prevent ANR
            stopSelf()
        }
    }

    private fun startProactiveMessaging() {
        serviceScope.launch {
            while (true) {
                if (!isUserActive && !isUserActiveStatic &&
                    System.currentTimeMillis() - lastInteractionTime > TimeUnit.MINUTES.toMillis(30)
                ) {
                    generateAIMessage()
                }
                delay(TimeUnit.MINUTES.toMillis(5))
            }
        }
    }

    private suspend fun generateAIMessage() {
        try {
            // Get recent context for future AI integration
            getRecentContext()
            // We'll use this prompt for future AI integration
            // val prompt = "Generate a natural, friendly message to re-engage the user. Be casual and helpful. " +
            //            "Here's recent context: $context"

            // Using a simple message for now since we don't have the API set up yet
            val message = when ((1..4).random()) {
                1 -> "Hey there! Just checking in. Need help with anything?"
                2 -> "I'm here if you need me. What would you like to work on today?"
                3 -> "Just a friendly reminder that I'm here to help whenever you need me!"
                else -> "I noticed you haven't been active for a while. Ready to continue?"
            }

            broadcastMessage(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRecentContext(): String {
        val minutesInactive = TimeUnit.MILLISECONDS.toMinutes(
            System.currentTimeMillis() - lastInteractionTime
        )
        return "User was last active $minutesInactive minutes ago"
    }

    private fun broadcastMessage(message: String) {
        sendBroadcast(Intent(PROACTIVE_MESSAGE_ACTION).apply {
            putExtra("message", message)
        })
    }

    private fun createNotification(): Notification {
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(notificationIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Genesis AI Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Background service for Genesis AI proactive messaging"
                    setShowBadge(false)
                    // Set no sound and no vibration for the notification channel
                    setSound(null, null)
                    enableVibration(false)
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue without notification channel if there's an error
            }
        }
    }
}
