package com.example.womencentricnetwork.Model

/**
 * Represents an incident report fetched from Firestore.
 *
 * Firestore collection: incidents/{incidentId}
 */
data class Incident(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String = "",
    val timestamp: Long = 0L,
    val reportedBy: String = ""
)

