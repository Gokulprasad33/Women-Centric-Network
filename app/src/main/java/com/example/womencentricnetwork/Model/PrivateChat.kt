package com.example.womencentricnetwork.Model

data class PrivateChat(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Long = 0L,
    val otherUserName: String = ""
)

