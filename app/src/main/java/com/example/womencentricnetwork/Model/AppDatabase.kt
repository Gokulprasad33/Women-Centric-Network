package com.example.womencentricnetwork.Model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.womencentricnetwork.Model.PersonalData.PersonalInfoModel
import com.example.womencentricnetwork.Model.Settings.EmergencyContactDao
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SosEventDao
import com.example.womencentricnetwork.Model.Settings.SosEventEntity
import com.example.womencentricnetwork.Model.Settings.TrustedLocationDao
import com.example.womencentricnetwork.Model.Settings.TrustedLocationEntity
import com.example.womencentricnetwork.PersonalInfoDao


@Database(
    entities = [
        PersonalInfoModel::class,
        EmergencyContactEntity::class,
        TrustedLocationEntity::class,
        SosEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personalInfoDao(): PersonalInfoDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun trustedLocationDao(): TrustedLocationDao
    abstract fun sosEventDao(): SosEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "WsnDB"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }


    }
}