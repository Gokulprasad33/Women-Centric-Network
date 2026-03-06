package com.example.womencentricnetwork

import androidx.room.*
import com.example.womencentricnetwork.Model.PersonalData.PersonalInfoModel
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalInfo(info: PersonalInfoModel)

    @Update
    suspend fun updatePersonalInfo(info: PersonalInfoModel)

    @Query("SELECT name FROM personalInfo")
    suspend fun getUserName(): String

    @Query("SELECT * FROM personalInfo")
    suspend fun getAllUsers(): List<PersonalInfoModel>
}
