package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.AuthManager
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.TrustedLocationEntity
import com.example.womencentricnetwork.R
import com.example.womencentricnetwork.ViewModel.SettingsViewModel

class SettingsScreen : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private val authManager = AuthManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    SettingsScreenContent(
                        viewModel = viewModel,
                        onLogout = {
                            authManager.logout()
                            findNavController().navigate(R.id.action_settingsFragment_to_loginFragment)
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Main composable
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreenContent(viewModel: SettingsViewModel, onLogout: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContactEntity?>(null) }

    var showLocationDialog by remember { mutableStateOf(false) }
    var editingLocation by remember { mutableStateOf<TrustedLocationEntity?>(null) }

    var showSosSavedSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showSosSavedSnackbar) {
        if (showSosSavedSnackbar) {
            snackbarHostState.showSnackbar("SOS message saved")
            showSosSavedSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Title ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Emergency Contacts ──────────────────────────────────────
            item {
                SectionHeader("Emergency Contacts")
            }
            item {
                if (uiState.emergencyContacts.size < 3) {
                    Text(
                        text = "⚠ Add at least 3 contacts for safety",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (uiState.emergencyContacts.isEmpty()) {
                item {
                    Text(
                        text = "No emergency contacts yet. Tap + to add.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.emergencyContacts, key = { it.id }) { contact ->
                    EmergencyContactItem(
                        contact = contact,
                        onEdit = {
                            editingContact = contact
                            showContactDialog = true
                        },
                        onDelete = { viewModel.deleteContact(contact.id) }
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        editingContact = null
                        viewModel.clearContactErrors()
                        showContactDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add contact")
                    Spacer(Modifier.width(4.dp))
                    Text("Add Contact")
                }
            }

            // ── Notification Preferences ────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Notification Preferences") }
            item {
                SettingsSwitch(
                    label = "Push Notifications",
                    checked = uiState.pushNotificationsEnabled,
                    onCheckedChange = { viewModel.setPushNotificationsEnabled(it) }
                )
            }
            item {
                SettingsSwitch(
                    label = "SMS Alerts",
                    checked = uiState.smsAlertsEnabled,
                    onCheckedChange = { viewModel.setSmsAlertsEnabled(it) }
                )
            }

            // ── Language ────────────────────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Language") }
            item {
                LanguageSelector(
                    selected = uiState.selectedLanguage,
                    onSelected = { viewModel.setPreferredLanguage(it) }
                )
            }

            // ── Location Sharing ────────────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Location Sharing") }
            item {
                SettingsSwitch(
                    label = "Allow sharing my location in SOS and other safety features",
                    checked = uiState.locationSharingEnabled,
                    onCheckedChange = { viewModel.setLocationSharingEnabled(it) }
                )
            }

            // ── SOS Message ─────────────────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("SOS Message") }
            item {
                SosMessageSection(
                    sosMessage = uiState.sosMessage,
                    error = uiState.sosMessageError,
                    onMessageChange = { viewModel.updateSosMessageLocally(it) },
                    onSave = {
                        viewModel.saveSosMessage()
                        if (uiState.sosMessageError == null) showSosSavedSnackbar = true
                    }
                )
            }

            // ── Trusted Locations ───────────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Trusted Locations") }
            item {
                Text(
                    text = "Save a trusted place like home or work for quick reference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.trustedLocations.isEmpty()) {
                item {
                    Text(
                        text = "No trusted locations added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.trustedLocations, key = { it.id }) { loc ->
                    TrustedLocationItem(
                        location = loc,
                        onEdit = {
                            editingLocation = loc
                            showLocationDialog = true
                        },
                        onDelete = { viewModel.deleteTrustedLocation(loc.id) }
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        editingLocation = null
                        viewModel.clearLocationErrors()
                        showLocationDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add location")
                    Spacer(Modifier.width(4.dp))
                    Text("Add Trusted Location")
                }
            }

            // ── Logout ───────────────────────────────────────────────────
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Account") }
            item {
                var showLogoutConfirm by remember { mutableStateOf(false) }

                Button(
                    onClick = { showLogoutConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    Spacer(Modifier.width(8.dp))
                    Text("Logout")
                }

                if (showLogoutConfirm) {
                    AlertDialog(
                        onDismissRequest = { showLogoutConfirm = false },
                        title = { Text("Logout") },
                        text = { Text("Are you sure you want to logout?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showLogoutConfirm = false
                                onLogout()
                            }) {
                                Text("Logout", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLogoutConfirm = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────
    if (showContactDialog) {
        AddEditContactDialog(
            existing = editingContact,
            nameError = uiState.contactNameError,
            phoneError = uiState.contactPhoneError,
            onDismiss = {
                showContactDialog = false
                viewModel.clearContactErrors()
            },
            onSave = { name, phone, relation ->
                viewModel.addOrUpdateContact(name, phone, relation, editingContact?.id)
                // Close only when there's no error — we rely on recomposition
                if (uiState.contactNameError == null && uiState.contactPhoneError == null) {
                    showContactDialog = false
                }
            }
        )
    }
    if (showLocationDialog) {
        AddEditTrustedLocationDialog(
            existing = editingLocation,
            labelError = uiState.locationLabelError,
            onDismiss = {
                showLocationDialog = false
                viewModel.clearLocationErrors()
            },
            onSave = { label, desc, lat, lon ->
                viewModel.addOrUpdateTrustedLocation(label, desc, lat, lon, editingLocation?.id)
                if (uiState.locationLabelError == null) {
                    showLocationDialog = false
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Reusable components
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Emergency Contact Item ──────────────────────────────────────────────

@Composable
fun EmergencyContactItem(
    contact: EmergencyContactEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Medium)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
                contact.relation?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Contact") },
            text = { Text("Remove ${contact.name} from emergency contacts?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Add / Edit Contact Dialog ───────────────────────────────────────────

@Composable
fun AddEditContactDialog(
    existing: EmergencyContactEntity?,
    nameError: String?,
    phoneError: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, relation: String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phoneNumber ?: "") }
    var relation by remember { mutableStateOf(existing?.relation ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Contact" else "Add Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    isError = phoneError != null,
                    supportingText = phoneError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relation (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, phone, relation) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Language Selector ───────────────────────────────────────────────────

@Composable
fun LanguageSelector(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("en" to "English", "ta" to "Tamil", "hi" to "Hindi")
    Column(Modifier.selectableGroup()) {
        options.forEach { (code, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == code,
                        onClick = { onSelected(code) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == code,
                    onClick = null // handled by selectable
                )
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

// ── SOS Message Section ─────────────────────────────────────────────────

@Composable
fun SosMessageSection(
    sosMessage: String,
    error: String?,
    onMessageChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = sosMessage,
            onValueChange = onMessageChange,
            label = { Text("Custom SOS Message") },
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("${sosMessage.length}/300")
                }
            },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave) { Text("Save SOS Message") }
    }
}

// ── Trusted Location Item ───────────────────────────────────────────────

@Composable
fun TrustedLocationItem(
    location: TrustedLocationEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(location.label, fontWeight = FontWeight.Medium)
                location.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (location.latitude != null && location.longitude != null) {
                    Text(
                        "📍 ${location.latitude}, ${location.longitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Location") },
            text = { Text("Remove \"${location.label}\" from trusted locations?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Add / Edit Trusted Location Dialog ──────────────────────────────────

@Composable
fun AddEditTrustedLocationDialog(
    existing: TrustedLocationEntity?,
    labelError: String?,
    onDismiss: () -> Unit,
    onSave: (label: String, description: String?, latitude: Double?, longitude: Double?) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var latText by remember { mutableStateOf(existing?.latitude?.toString() ?: "") }
    var lonText by remember { mutableStateOf(existing?.longitude?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Location" else "Add Trusted Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label *  (e.g. Home, Work)") },
                    isError = labelError != null,
                    supportingText = labelError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Address (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    label,
                    description.ifBlank { null },
                    latText.toDoubleOrNull(),
                    lonText.toDoubleOrNull()
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}