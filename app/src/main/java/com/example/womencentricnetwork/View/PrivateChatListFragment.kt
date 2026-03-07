package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.PrivateChat
import com.example.womencentricnetwork.Model.SafetyState
import com.example.womencentricnetwork.Model.SosAlert
import com.example.womencentricnetwork.Model.UserPresence
import com.example.womencentricnetwork.R
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class PrivateChatListFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var chatListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null
    private var sosAlertListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    ChatHubScreen(
                        firestoreManager = firestoreManager,
                        onRegisterChatListener = { chatListener = it },
                        onRegisterPresenceListener = { presenceListener = it },
                        onRegisterSosAlertListener = { sosAlertListener = it },
                        onChatClick = { chatId, otherName ->
                            val bundle = Bundle().apply {
                                putString("chatId", chatId)
                                putString("otherName", otherName)
                            }
                            findNavController().navigate(R.id.privateChatFragment, bundle)
                        },
                        onFindUsersClick = {
                            findNavController().navigate(R.id.userSearchFragment)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatListener?.remove()
        presenceListener?.remove()
        sosAlertListener?.remove()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat Hub Screen — 3 sections: People Nearby, SOS Alerts, Chats
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ChatHubScreen(
    firestoreManager: FirestoreManager,
    onRegisterChatListener: (ListenerRegistration) -> Unit,
    onRegisterPresenceListener: (ListenerRegistration) -> Unit,
    onRegisterSosAlertListener: (ListenerRegistration) -> Unit,
    onChatClick: (chatId: String, otherName: String) -> Unit,
    onFindUsersClick: () -> Unit = {}
) {
    var chats by remember { mutableStateOf<List<PrivateChat>>(emptyList()) }
    var presenceList by remember { mutableStateOf<List<UserPresence>>(emptyList()) }
    var sosAlerts by remember { mutableStateOf<List<SosAlert>>(emptyList()) }
    var showStatusDialog by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateSdf = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

    DisposableEffect(Unit) {
        val chatReg = firestoreManager.listenForPrivateChats { chats = it }
        onRegisterChatListener(chatReg)

        val presenceReg = firestoreManager.listenForPresence { presenceList = it }
        onRegisterPresenceListener(presenceReg)

        val sosReg = firestoreManager.listenForSosAlerts { sosAlerts = it }
        onRegisterSosAlertListener(sosReg)

        onDispose {
            chatReg.remove()
            presenceReg.remove()
            sosReg.remove()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onFindUsersClick) {
                Icon(Icons.Default.Add, contentDescription = "Find users")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showStatusDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Set status")
                    }
                }
            }

            // ── Section 1: People Nearby (horizontal scroll with status notes) ───
            if (presenceList.isNotEmpty()) {
                item {
                    Text("People Nearby", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(presenceList, key = { it.uid }) { presence ->
                            PresenceAvatarItem(presence)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Section 2: SOS Alerts ──────────────────────────────────
            if (sosAlerts.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("🚨 SOS Alerts", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = Color(0xFFF44336))
                    Spacer(Modifier.height(8.dp))
                }
                items(sosAlerts.take(5), key = { it.id }) { alert ->
                    SosAlertCard(alert, sdf)
                }
            }

            // ── Section 3: Chats ───────────────────────────────────────
            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Chats", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }

            if (chats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No conversations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onFindUsersClick) {
                                Text("Find users to chat with")
                            }
                        }
                    }
                }
            } else {
                items(chats, key = { it.id }) { chat ->
                    val otherPresence = presenceList.find { p ->
                        chat.participants.contains(p.uid)
                    }
                    ChatItemCard(chat, otherPresence, dateSdf) {
                        onChatClick(chat.id, chat.otherUserName)
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showStatusDialog) {
        StatusNoteDialog(
            firestoreManager = firestoreManager,
            onDismiss = { showStatusDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Presence Avatar Item (Instagram Notes style)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PresenceAvatarItem(presence: UserPresence) {
    val borderColor = when (presence.safetyStateEnum) {
        SafetyState.SAFE -> Color(0xFF4CAF50)
        SafetyState.OUTSIDE -> Color(0xFFFF9800)
        SafetyState.SOS -> Color(0xFFF44336)
        SafetyState.OFFLINE -> Color.Gray
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(3.dp, borderColor, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = presence.name.take(1).uppercase(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = borderColor
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            presence.name.split(" ").first(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (presence.status.isNotBlank()) {
            Text(
                "\"${presence.status}\"",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SOS Alert Card
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SosAlertCard(alert: SosAlert, sdf: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF44336)),
                contentAlignment = Alignment.Center
            ) {
                Text("🚨", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${alert.name} triggered SOS",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (alert.timestamp > 0) {
                    Text(
                        sdf.format(Date(alert.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Chat Item Card (with presence indicator)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ChatItemCard(
    chat: PrivateChat,
    otherPresence: UserPresence?,
    sdf: SimpleDateFormat,
    onClick: () -> Unit
) {
    val presenceColor = when (otherPresence?.safetyStateEnum) {
        SafetyState.SAFE -> Color(0xFF4CAF50)
        SafetyState.OUTSIDE -> Color(0xFFFF9800)
        SafetyState.SOS -> Color(0xFFF44336)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chat.otherUserName.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(presenceColor)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(chat.otherUserName, fontWeight = FontWeight.SemiBold)
                    if (chat.lastTimestamp > 0) {
                        Text(
                            formatRelativeTime(chat.lastTimestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (chat.lastMessage.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (otherPresence?.status?.isNotBlank() == true) {
                    Text(
                        "\"${otherPresence.status}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = presenceColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Status Note Dialog
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun StatusNoteDialog(firestoreManager: FirestoreManager, onDismiss: () -> Unit) {
    var status by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Your Status") },
        text = {
            Column {
                Text("Share what you're up to (visible to other users)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = status,
                    onValueChange = { if (it.length <= 60) status = it },
                    label = { Text("Status") },
                    placeholder = { Text("At College, Going Home...") },
                    supportingText = { Text("${status.length}/60") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                firestoreManager.updateUserStatus(status)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Relative time helper
// ═══════════════════════════════════════════════════════════════════════════

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}
