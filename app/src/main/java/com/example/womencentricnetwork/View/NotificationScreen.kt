package com.example.womencentricnetwork.View

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.SosAlert
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class NotificationScreen : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var alertListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    NotificationsScreen(
                        firestoreManager = firestoreManager,
                        onRegisterListener = { alertListener = it }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alertListener?.remove()
        alertListener = null
    }
}

// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NotificationsScreen(
    firestoreManager: FirestoreManager,
    onRegisterListener: (ListenerRegistration) -> Unit
) {
    var alerts by remember { mutableStateOf<List<SosAlert>>(emptyList()) }

    DisposableEffect(Unit) {
        val reg = firestoreManager.listenForAllAlerts { alerts = it }
        onRegisterListener(reg)
        onDispose { reg.remove() }
    }

    val emergencyAlerts = alerts.filter { it.type == "SOS" }
    val networkAlerts = alerts.filter { it.type == "NETWORK_ALERT" }

    Scaffold { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Emergency Alerts (RED) ─────────────────────────────────
            if (emergencyAlerts.isNotEmpty()) {
                item {
                    SectionHeader("🚨 Emergency Alerts", Color(0xFFF44336))
                }
                items(emergencyAlerts, key = { it.id }) { alert ->
                    NotificationAlertCard(
                        alert = alert,
                        accentColor = Color(0xFFF44336),
                        onViewed = { firestoreManager.markAlertViewed(it) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Community / Network Alerts (ORANGE) ────────────────────
            if (networkAlerts.isNotEmpty()) {
                item {
                    SectionHeader("⚠️ Community Alerts", Color(0xFFFF9800))
                }
                items(networkAlerts, key = { it.id }) { alert ->
                    NotificationAlertCard(
                        alert = alert,
                        accentColor = Color(0xFFFF9800),
                        onViewed = { firestoreManager.markAlertViewed(it) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Empty State ────────────────────────────────────────────
            if (alerts.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔔", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No notifications yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Alerts from the last 24 hours will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, color: Color) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NotificationAlertCard(
    alert: SosAlert,
    accentColor: Color,
    onViewed: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val timeText = remember(alert.timestamp) {
        val diff = System.currentTimeMillis() - alert.timestamp
        val minutes = diff / (60_000)
        val hours = diff / (3_600_000)
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                .format(Date(alert.timestamp))
        }
    }

    val emoji = if (alert.type == "SOS") "🚨" else "⚠️"
    val title = if (alert.type == "SOS") "${alert.name} needs help!"
                else "${alert.name} sent an alert"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.4f))
        )
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            // Content
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (alert.message.isNotBlank()) {
                    Text(
                        alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Navigate button if location available
            if (alert.lat != 0.0 && alert.lon != 0.0) {
                FilledTonalButton(
                    onClick = {
                        onViewed(alert.id)
                        val uri = Uri.parse(
                            "google.navigation:q=${alert.lat},${alert.lon}&mode=w"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val webUri = Uri.parse(
                                "https://maps.google.com/?q=${alert.lat},${alert.lon}"
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Navigate", fontSize = 12.sp)
                }
            }
        }
    }
}