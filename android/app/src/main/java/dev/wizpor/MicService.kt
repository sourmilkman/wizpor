package dev.wizpor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Foreground service that keeps microphone access available while the bubble is
 * used over other apps. It must be started while wizpor is visible (Android rule).
 */
class MicService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "wizpor ready", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the voice bubble able to use the microphone"
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("wizpor is ready")
            .setContentText("Tap the bubble to dictate")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            running = true
        } catch (e: Exception) {
            Log.w("wizpor", "MicService could not start in foreground", e)
            running = false
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "ready"
        @Volatile var running = false

        fun start(ctx: Context): Boolean = try {
            ctx.startForegroundService(Intent(ctx, MicService::class.java))
            true
        } catch (e: Exception) {
            Log.w("wizpor", "MicService start refused", e)
            false
        }
    }
}
