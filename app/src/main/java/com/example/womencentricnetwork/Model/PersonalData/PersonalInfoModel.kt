package com.example.womencentricnetwork.Model.PersonalData

import android.R
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personalInfo")
data class PersonalInfoModel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val profilePicturePath: String,
    val userType: String,

    val email : String,
    val phoneNo : String,

    val emergencyNumber : List<String>,
    val updatedAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),

    val lat : String,
    val lon : String,

    val isVerified: Boolean = false

)