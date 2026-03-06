package com.example.womencentricnetwork.Model.Settings

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY id ASC")
    fun getAll(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Update
    suspend fun update(contact: EmergencyContactEntity)

    @Delete
    suspend fun delete(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

