package com.kemzy.liveavatar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground-service host for long-lived live-camera processing.
 * The actual CameraX/face-processing pipeline is attached to this lifecycle
 * in the next integration step; the service deliberately does not fake output.
 */
class LiveCameraService : Service() {
    companion object {
        private const val CHANNEL_ID = "kemzy_live_camera"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.kemzy.liveavatar.START_LIVE"
        const val ACTION_STOP = "com.kemzy.liveavatar.STOP_LIVE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("Kemzy-LiveAvatar")
                    .setContentText("Live camera processing is running")
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build()

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Kemzy live camera",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
