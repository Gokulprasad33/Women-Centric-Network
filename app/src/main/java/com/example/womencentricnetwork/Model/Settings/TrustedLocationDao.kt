package com.example.womencentricnetwork.Model.Settings

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedLocationDao {

    @Query("SELECT * FROM trusted_locations ORDER BY id ASC")
    fun getAll(): Flow<List<TrustedLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: TrustedLocationEntity): Long

    @Update
    suspend fun update(location: TrustedLocationEntity)

    @Delete
    suspend fun delete(location: TrustedLocationEntity)

    @Query("DELETE FROM trusted_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

