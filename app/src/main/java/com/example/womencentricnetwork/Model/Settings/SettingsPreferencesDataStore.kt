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
    val sosMessage: String = "I need help. This is my current location:"
)

class SettingsPreferencesDataStore(private val context: Context) {

    companion object {
        val PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
        val SMS_ALERTS_ENABLED = booleanPreferencesKey("sms_alerts_enabled")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val LOCATION_SHARING_ENABLED = booleanPreferencesKey("location_sharing_enabled")
        val SOS_CUSTOM_MESSAGE = stringPreferencesKey("sos_custom_message")
    }

    val settingsFlow: Flow<SettingsPreferences> = context.dataStore.data.map { prefs ->
        SettingsPreferences(
            pushNotificationsEnabled = prefs[PUSH_NOTIFICATIONS_ENABLED] ?: true,
            smsAlertsEnabled = prefs[SMS_ALERTS_ENABLED] ?: false,
            preferredLanguage = prefs[PREFERRED_LANGUAGE] ?: "en",
            locationSharingEnabled = prefs[LOCATION_SHARING_ENABLED] ?: true,
            sosMessage = prefs[SOS_CUSTOM_MESSAGE] ?: "I need help. This is my current location:"
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
}

