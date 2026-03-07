package com.example.womencentricnetwork.Model

/**
 * Represents an in-app alert broadcast.
 * Stored in Firestore: alerts/{alertId}
 *
 * Types: SOS, NETWORK_ALERT
 */
data class SosAlert(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val type: String = "SOS",
    val message: String = "",
    val timestamp: Long = 0L,
    val active: Boolean = true
)

enum class AlertType {
    SOS,
    NETWORK_ALERT
}
