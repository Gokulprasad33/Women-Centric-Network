package com.example.womencentricnetwork.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.womencentricnetwork.Model.AppDatabase
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SettingsPreferencesDataStore
import com.example.womencentricnetwork.Model.Settings.TrustedLocationEntity
import com.example.womencentricnetwork.Repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val emergencyContacts: List<EmergencyContactEntity> = emptyList(),
    val trustedLocations: List<TrustedLocationEntity> = emptyList(),
    val pushNotificationsEnabled: Boolean = true,
    val smsAlertsEnabled: Boolean = false,
    val selectedLanguage: String = "en",
    val locationSharingEnabled: Boolean = true,
    val sosMessage: String = "I need help. This is my current location:",
    val sosMessageError: String? = null,
    val contactNameError: String? = null,
    val contactPhoneError: String? = null,
    val locationLabelError: String? = null,
    val isLoading: Boolean = true,
    val liveLocationDuration: String = "OFF",     // OFF, 30_MIN, 1_HOUR, UNTIL_DISABLED
    val userStatus: String = "",                   // Instagram Notes style (max 60 chars)
    val safetyState: String = "SAFE"               // SAFE, OUTSIDE, SOS, OFFLINE
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SettingsRepository

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        val prefsDataStore = SettingsPreferencesDataStore(application)
        repository = SettingsRepository(
            emergencyContactDao = db.emergencyContactDao(),
            trustedLocationDao = db.trustedLocationDao(),
            preferencesDataStore = prefsDataStore
        )
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.observeEmergencyContacts().collect { contacts ->
                _uiState.update { it.copy(emergencyContacts = contacts) }
            }
        }
        viewModelScope.launch {
            repository.observeTrustedLocations().collect { locations ->
                _uiState.update { it.copy(trustedLocations = locations) }
            }
        }
        viewModelScope.launch {
            repository.observePreferences().collect { prefs ->
                _uiState.update {
                    it.copy(
                        pushNotificationsEnabled = prefs.pushNotificationsEnabled,
                        smsAlertsEnabled = prefs.smsAlertsEnabled,
                        selectedLanguage = prefs.preferredLanguage,
                        locationSharingEnabled = prefs.locationSharingEnabled,
                        sosMessage = prefs.sosMessage,
                        liveLocationDuration = prefs.liveLocationDuration,
                        userStatus = prefs.userStatus,
                        safetyState = prefs.safetyState,
                        isLoading = false
                    )
                }
            }
        }
    }

    // ── Emergency Contacts ──────────────────────────────────────────────

    fun addOrUpdateContact(name: String, phone: String, relation: String?, existingId: Long? = null) {
        val trimmedName = name.trim()
        val trimmedPhone = phone.trim()

        // Validate
        val nameError = if (trimmedName.isBlank()) "Name cannot be empty" else null
        val phoneError = when {
            trimmedPhone.isBlank() -> "Phone number cannot be empty"
            trimmedPhone.length < 7 -> "Phone number is too short"
            !trimmedPhone.matches(Regex("^[+]?[0-9\\- ]{7,15}$")) -> "Invalid phone number format"
            else -> null
        }

        if (nameError != null || phoneError != null) {
            _uiState.update { it.copy(contactNameError = nameError, contactPhoneError = phoneError) }
            return
        }

        _uiState.update { it.copy(contactNameError = null, contactPhoneError = null) }

        viewModelScope.launch {
            val contact = EmergencyContactEntity(
                id = existingId ?: 0,
                name = trimmedName,
                phoneNumber = trimmedPhone,
                relation = relation?.trim()?.ifBlank { null }
            )
            if (existingId != null) {
                repository.updateContact(contact)
            } else {
                repository.addContact(contact)
            }
        }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
        }
    }

    fun clearContactErrors() {
        _uiState.update { it.copy(contactNameError = null, contactPhoneError = null) }
    }

    // ── Notification Preferences ────────────────────────────────────────

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setPushNotificationsEnabled(enabled) }
    }

    fun setSmsAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSmsAlertsEnabled(enabled) }
    }

    // ── Language ────────────────────────────────────────────────────────

    fun setPreferredLanguage(code: String) {
        viewModelScope.launch { repository.setPreferredLanguage(code) }
    }

    // ── Location Sharing ────────────────────────────────────────────────

    fun setLocationSharingEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLocationSharingEnabled(enabled) }
    }

    // ── SOS Message ─────────────────────────────────────────────────────

    fun updateSosMessageLocally(text: String) {
        _uiState.update { it.copy(sosMessage = text, sosMessageError = null) }
    }

    fun saveSosMessage() {
        val msg = _uiState.value.sosMessage.trim()
        val error = when {
            msg.isBlank() -> "SOS message cannot be empty"
            msg.length > 300 -> "SOS message must be 300 characters or fewer"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(sosMessageError = error) }
            return
        }
        _uiState.update { it.copy(sosMessageError = null) }
        viewModelScope.launch { repository.setSosMessage(msg) }
    }

    // ── Trusted Locations ───────────────────────────────────────────────

    fun addOrUpdateTrustedLocation(
        label: String,
        description: String?,
        latitude: Double?,
        longitude: Double?,
        existingId: Long? = null
    ) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) {
            _uiState.update { it.copy(locationLabelError = "Label cannot be empty") }
            return
        }
        _uiState.update { it.copy(locationLabelError = null) }

        viewModelScope.launch {
            val location = TrustedLocationEntity(
                id = existingId ?: 0,
                label = trimmedLabel,
                description = description?.trim()?.ifBlank { null },
                latitude = latitude,
                longitude = longitude
            )
            if (existingId != null) {
                repository.updateTrustedLocation(location)
            } else {
                repository.addTrustedLocation(location)
            }
        }
    }

    fun deleteTrustedLocation(id: Long) {
        viewModelScope.launch { repository.deleteTrustedLocation(id) }
    }

    fun clearLocationErrors() {
        _uiState.update { it.copy(locationLabelError = null) }
    }

    // ── Live Location ────────────────────────────────────────────────────

    fun setLiveLocationDuration(duration: String) {
        viewModelScope.launch { repository.setLiveLocationDuration(duration) }
    }

    // ── User Status (Instagram Notes) ────────────────────────────────────

    fun setUserStatus(status: String) {
        viewModelScope.launch { repository.setUserStatus(status.take(60)) }
    }

    // ── Safety State ─────────────────────────────────────────────────────

    fun setSafetyState(state: String) {
        viewModelScope.launch { repository.setSafetyState(state) }
    }
}

