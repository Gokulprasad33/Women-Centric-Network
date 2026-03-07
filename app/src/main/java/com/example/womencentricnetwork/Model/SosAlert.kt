package com.example.womencentricnetwork.Model

/**
 * Represents an in-app SOS alert broadcast.
 * Stored in Firestore: sosAlerts/{alertId}
 */
data class SosAlert(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val timestamp: Long = 0L
)

