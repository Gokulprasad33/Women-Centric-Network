package com.example.womencentricnetwork.Firebase

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles incoming FCM push notifications and token refresh.
 *
 * Token lifecycle:
 *   1. App installs → onNewToken() fires → token saved to Firestore
 *   2. Token rotates → onNewToken() fires again → Firestore updated
 *   3. Server sends notification → onMessageReceived() → system notification shown
 */
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    private val notificationHelper by lazy { NotificationHelper(applicationContext) }

    // ── Token Refresh ───────────────────────────────────────────────────

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: $token")

        // Save token to Firestore (only if user is logged in)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestoreManager = FirestoreManager()
                firestoreManager.saveFcmToken(token)
                Log.d(TAG, "FCM token saved to Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save FCM token: ${e.message}")
            }
        }
    }

    // ── Incoming Message ────────────────────────────────────────────────

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received from: ${message.from}")

        // Handle notification payload
        message.notification?.let { notification ->
            notificationHelper.showGeneralNotification(
                title = notification.title ?: "WCN Alert",
                body = notification.body ?: "You have a new alert"
            )
        }

        // Handle data payload (for custom handling like SOS alerts from other users)
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "FCM data payload: ${message.data}")
            val title = message.data["title"] ?: "WCN Alert"
            val body = message.data["body"] ?: "You have a new alert"
            val type = message.data["type"] ?: "general"

            when (type) {
                "sos_alert" -> notificationHelper.showSosAlert(title, body,
                    notificationId = System.currentTimeMillis().toInt())
                "nearby_user" -> notificationHelper.showNearbyAlert(title, body,
                    notificationId = System.currentTimeMillis().toInt())
                "community_help" -> notificationHelper.showCommunityAlert(title, body,
                    notificationId = System.currentTimeMillis().toInt())
                "chat" -> notificationHelper.showChatNotification(title, body,
                    notificationId = System.currentTimeMillis().toInt())
                else -> notificationHelper.showGeneralNotification(title, body,
                    notificationId = System.currentTimeMillis().toInt())
            }
        }
    }
}

