package com.example.womencentricnetwork.Repository

import com.example.womencentricnetwork.Model.Settings.EmergencyContactDao
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SettingsPreferences
import com.example.womencentricnetwork.Model.Settings.SettingsPreferencesDataStore
import com.example.womencentricnetwork.Model.Settings.TrustedLocationDao
import com.example.womencentricnetwork.Model.Settings.TrustedLocationEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val emergencyContactDao: EmergencyContactDao,
    private val trustedLocationDao: TrustedLocationDao,
    private val preferencesDataStore: SettingsPreferencesDataStore
) {

    // ── Emergency Contacts ──────────────────────────────────────────────

    fun observeEmergencyContacts(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getAll()

    suspend fun addContact(contact: EmergencyContactEntity) {
        emergencyContactDao.insert(contact)
    }

    suspend fun updateContact(contact: EmergencyContactEntity) {
        emergencyContactDao.update(contact)
    }

    suspend fun deleteContact(id: Long) {
        emergencyContactDao.deleteById(id)
    }

    // ── Trusted Locations ───────────────────────────────────────────────

    fun observeTrustedLocations(): Flow<List<TrustedLocationEntity>> =
        trustedLocationDao.getAll()

    suspend fun addTrustedLocation(location: TrustedLocationEntity) {
        trustedLocationDao.insert(location)
    }

    suspend fun updateTrustedLocation(location: TrustedLocationEntity) {
        trustedLocationDao.update(location)
    }

    suspend fun deleteTrustedLocation(id: Long) {
        trustedLocationDao.deleteById(id)
    }

    // ── DataStore Preferences ───────────────────────────────────────────

    fun observePreferences(): Flow<SettingsPreferences> =
        preferencesDataStore.settingsFlow

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        preferencesDataStore.setPushNotificationsEnabled(enabled)
    }

    suspend fun setSmsAlertsEnabled(enabled: Boolean) {
        preferencesDataStore.setSmsAlertsEnabled(enabled)
    }

    suspend fun setPreferredLanguage(code: String) {
        preferencesDataStore.setPreferredLanguage(code)
    }

    suspend fun setLocationSharingEnabled(enabled: Boolean) {
        preferencesDataStore.setLocationSharingEnabled(enabled)
    }

    suspend fun setSosMessage(message: String) {
        preferencesDataStore.setSosMessage(message)
    }
}

