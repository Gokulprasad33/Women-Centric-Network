package com.example.womencentricnetwork.Model.Settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

data class SettingsPreferences(
    val pushNotificationsEnabled: Boolean = true,
    val smsAlertsEnabled: Boolean = false,
    val preferredLanguage: String = "en",
    val locationSharingEnabled: Boolean = true,
    val sosMessage: String = "I need help. This is my current location:",
    val liveLocationDuration: String = "OFF",   // OFF, 30_MIN, 1_HOUR, UNTIL_DISABLED
    val userStatus: String = "",                 // Instagram Notes style short status (max 60 chars)
    val safetyState: String = "SAFE"             // SAFE, OUTSIDE, SOS, OFFLINE
)

class SettingsPreferencesDataStore(private val context: Context) {

    companion object {
        val PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
        val SMS_ALERTS_ENABLED = booleanPreferencesKey("sms_alerts_enabled")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val LOCATION_SHARING_ENABLED = booleanPreferencesKey("location_sharing_enabled")
        val SOS_CUSTOM_MESSAGE = stringPreferencesKey("sos_custom_message")
        val LIVE_LOCATION_DURATION = stringPreferencesKey("live_location_duration")
        val USER_STATUS = stringPreferencesKey("user_status")
        val SAFETY_STATE = stringPreferencesKey("safety_state")
        val LIVE_LOCATION_START_TIME = stringPreferencesKey("live_location_start_time")
    }

    val settingsFlow: Flow<SettingsPreferences> = context.dataStore.data.map { prefs ->
        SettingsPreferences(
            pushNotificationsEnabled = prefs[PUSH_NOTIFICATIONS_ENABLED] ?: true,
            smsAlertsEnabled = prefs[SMS_ALERTS_ENABLED] ?: false,
            preferredLanguage = prefs[PREFERRED_LANGUAGE] ?: "en",
            locationSharingEnabled = prefs[LOCATION_SHARING_ENABLED] ?: true,
            sosMessage = prefs[SOS_CUSTOM_MESSAGE] ?: "I need help. This is my current location:",
            liveLocationDuration = prefs[LIVE_LOCATION_DURATION] ?: "OFF",
            userStatus = prefs[USER_STATUS] ?: "",
            safetyState = prefs[SAFETY_STATE] ?: "SAFE"
        )
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PUSH_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSmsAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SMS_ALERTS_ENABLED] = enabled }
    }

    suspend fun setPreferredLanguage(code: String) {
        context.dataStore.edit { it[PREFERRED_LANGUAGE] = code }
    }

    suspend fun setLocationSharingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOCATION_SHARING_ENABLED] = enabled }
    }

    suspend fun setSosMessage(message: String) {
        context.dataStore.edit { it[SOS_CUSTOM_MESSAGE] = message }
    }

    suspend fun setLiveLocationDuration(duration: String) {
        context.dataStore.edit {
            it[LIVE_LOCATION_DURATION] = duration
            if (duration != "OFF") {
                it[LIVE_LOCATION_START_TIME] = System.currentTimeMillis().toString()
            }
        }
    }

    suspend fun setUserStatus(status: String) {
        context.dataStore.edit { it[USER_STATUS] = status.take(60) }
    }

    suspend fun setSafetyState(state: String) {
        context.dataStore.edit { it[SAFETY_STATE] = state }
    }

    fun getLiveLocationStartTime(): Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LIVE_LOCATION_START_TIME]?.toLongOrNull() ?: 0L
    }
}

