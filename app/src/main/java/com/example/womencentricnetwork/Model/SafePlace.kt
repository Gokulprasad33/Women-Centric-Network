package com.example.womencentricnetwork.Model

/**
 * Represents a safe place loaded from local JSON (OSM/GeoJSON) assets.
 */
data class SafePlace(
    val id: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double
)
