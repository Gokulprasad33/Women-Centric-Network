package com.example.womencentricnetwork.Repository

import android.util.Log
import com.example.womencentricnetwork.Firebase.FirestoreManager
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
    private val preferencesDataStore: SettingsPreferencesDataStore,
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    companion object {
        private const val TAG = "SettingsRepository"
    }

    // ── Emergency Contacts ──────────────────────────────────────────────

    fun observeEmergencyContacts(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getAll()

    suspend fun addContact(contact: EmergencyContactEntity) {
        val id = emergencyContactDao.insert(contact)
        // Sync to Firestore (non-blocking — local Room is the source of truth)
        try {
            val savedContact = contact.copy(id = id)
            firestoreManager.syncContact(savedContact)
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed on add: ${e.message}")
        }
    }

    suspend fun updateContact(contact: EmergencyContactEntity) {
        emergencyContactDao.update(contact)
        // Sync to Firestore
        try {
            firestoreManager.syncContact(contact)
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed on update: ${e.message}")
        }
    }

    suspend fun deleteContact(id: Long) {
        emergencyContactDao.deleteById(id)
        // Delete from Firestore
        try {
            firestoreManager.deleteContact(id)
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed on delete: ${e.message}")
        }
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

    // ── Live Location ────────────────────────────────────────────────────

    suspend fun setLiveLocationDuration(duration: String) {
        preferencesDataStore.setLiveLocationDuration(duration)
    }

    // ── User Status ──────────────────────────────────────────────────────

    suspend fun setUserStatus(status: String) {
        preferencesDataStore.setUserStatus(status)
    }

    // ── Safety State ─────────────────────────────────────────────────────

    suspend fun setSafetyState(state: String) {
        preferencesDataStore.setSafetyState(state)
    }
}

