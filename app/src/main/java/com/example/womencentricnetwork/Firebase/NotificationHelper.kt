package com.example.womencentricnetwork.Firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.womencentricnetwork.MainActivity
import com.example.womencentricnetwork.R

/**
 * Utility class for building and displaying notifications.
 *
 * Channels:
 *   - sos_channel      → SOS emergency alerts (IMPORTANCE_HIGH)
 *   - community_channel → Community help requests (IMPORTANCE_DEFAULT)
 *   - general_channel   → General app updates (IMPORTANCE_LOW)
 */
class NotificationHelper(private val context: Context) {

    companion object {
        // Channel IDs
        const val CHANNEL_SOS = "sos_channel"
        const val CHANNEL_COMMUNITY = "community_channel"
        const val CHANNEL_GENERAL = "general_channel"

        // Notification IDs (use unique IDs to avoid overwriting)
        const val NOTIFICATION_SOS = 1001
        const val NOTIFICATION_COMMUNITY = 1002
        const val NOTIFICATION_GENERAL = 1003
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    // ── Create Notification Channels (Android O+) ───────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sosChannel = NotificationChannel(
                CHANNEL_SOS,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS alert notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setShowBadge(true)
            }

            val communityChannel = NotificationChannel(
                CHANNEL_COMMUNITY,
                "Community Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Community help requests and safety alerts"
                enableVibration(true)
            }

            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "App updates and informational notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(sosChannel, communityChannel, generalChannel)
            )
        }
    }

    // ── SOS Emergency Alert ─────────────────────────────────────────────

    fun showSosAlert(title: String, body: String, notificationId: Int = NOTIFICATION_SOS) {
        val notification = buildNotification(
            channelId = CHANNEL_SOS,
            title = "🚨 $title",
            body = body,
            priority = NotificationCompat.PRIORITY_HIGH
        )
        notificationManager.notify(notificationId, notification)
    }

    // ── Community Alert ─────────────────────────────────────────────────

    fun showCommunityAlert(title: String, body: String, notificationId: Int = NOTIFICATION_COMMUNITY) {
        val notification = buildNotification(
            channelId = CHANNEL_COMMUNITY,
            title = "🤝 $title",
            body = body,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
        notificationManager.notify(notificationId, notification)
    }

    // ── General Notification ────────────────────────────────────────────

    fun showGeneralNotification(title: String, body: String, notificationId: Int = NOTIFICATION_GENERAL) {
        val notification = buildNotification(
            channelId = CHANNEL_GENERAL,
            title = title,
            body = body,
            priority = NotificationCompat.PRIORITY_LOW
        )
        notificationManager.notify(notificationId, notification)
    }

    // ── Build Notification ──────────────────────────────────────────────

    private fun buildNotification(
        channelId: String,
        title: String,
        body: String,
        priority: Int
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    // ── Cancel Notifications ────────────────────────────────────────────

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}

