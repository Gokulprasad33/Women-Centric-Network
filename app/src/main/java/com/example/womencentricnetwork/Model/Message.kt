package com.example.womencentricnetwork.Model

/**
 * Represents a chat message in the community chat.
 *
 * Firestore collection: messages/{messageId}
 */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val messageText: String = "",
    val roomId: String = "",
    val timestamp: Long = 0L
)

