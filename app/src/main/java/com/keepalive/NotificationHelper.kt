package com.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "keep_alive_channel"
    const val ALERT_CHANNEL_ID = "keep_alive_alerts"
    const val NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Keep-Alive Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring service status"
            setSound(null, null)
        }
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Keep-Alive Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Server down alerts"
        }
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    fun buildNotification(context: Context, urlCount: Int): Notification {
        val pendingIntent = activityPendingIntent(context)
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Keep-Alive activ")
            .setContentText("Se monitorizeaza $urlCount URL-uri")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showDownAlert(context: Context, url: String, failureCount: Int) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("Server posibil down")
            .setContentText("$url - $failureCount ping-uri consecutive au esuat")
            .setStyle(Notification.BigTextStyle().bigText("$url - $failureCount ping-uri consecutive au esuat"))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(activityPendingIntent(context))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(alertNotificationId(url), notification)
    }

    private fun activityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun alertNotificationId(url: String): Int {
        return 10_000 + (url.hashCode() and 0x0FFF_FFFF)
    }
}
