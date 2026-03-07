package com.example.womencentricnetwork.Model

/**
 * Safety states for user presence tracking.
 */
enum class SafetyState {
    SAFE,       // Green — user is at a safe location
    OUTSIDE,    // Orange — user is outside / traveling
    SOS,        // Red — user has triggered SOS
    OFFLINE     // Grey — user is offline or hasn't updated recently
}

/**
 * Represents a user's presence in the app.
 * Stored in Firestore: presence/{uid}
 */
data class UserPresence(
    val uid: String = "",
    val name: String = "",
    val status: String = "",              // Short status message (max 60 chars), Instagram Notes style
    val liveLocationEnabled: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val lastUpdated: Long = 0L,
    val safetyState: String = SafetyState.OFFLINE.name  // SAFE, OUTSIDE, SOS, OFFLINE
) {
    val safetyStateEnum: SafetyState
        get() = try { SafetyState.valueOf(safetyState) } catch (_: Exception) { SafetyState.OFFLINE }
}

