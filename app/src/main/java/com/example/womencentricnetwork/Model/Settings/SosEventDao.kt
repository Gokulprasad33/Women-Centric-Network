package com.example.womencentricnetwork.Model.Settings

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SosEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SosEventEntity)

    @Query("SELECT * FROM sos_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SosEventEntity>>

    @Query("SELECT * FROM sos_events ORDER BY timestamp DESC")
    suspend fun getAllEventsList(): List<SosEventEntity>
}

