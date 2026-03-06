package com.example.womencentricnetwork.Model.Settings

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_locations")
data class TrustedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

