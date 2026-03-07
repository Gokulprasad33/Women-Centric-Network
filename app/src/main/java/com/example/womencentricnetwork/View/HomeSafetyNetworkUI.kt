package com.example.womencentricnetwork.View

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.womencentricnetwork.Model.SafetyState
import com.example.womencentricnetwork.Model.SosAlert
import com.example.womencentricnetwork.Model.UserPresence

// ═══════════════════════════════════════════════════════════════════════════
// Friend Status Row — horizontal list above SOS button
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun FriendStatusRow(
    presenceList: List<UserPresence>,
    userLat: Double?,
    userLon: Double?
) {
    if (presenceList.isEmpty()) return

    Column {
        Text(
            "Friends Nearby",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presenceList, key = { it.uid }) { presence ->
                FriendStatusItem(
                    presence = presence,
                    userLat = userLat,
                    userLon = userLon
                )
            }
        }
    }
}

@Composable
fun FriendStatusItem(
    presence: UserPresence,
    userLat: Double?,
    userLon: Double?
) {
    val borderColor = when (presence.safetyStateEnum) {
        SafetyState.SAFE -> Color(0xFF4CAF50)
        SafetyState.OUTSIDE -> Color(0xFFFF9800)
        SafetyState.SOS -> Color(0xFFF44336)
        SafetyState.OFFLINE -> Color.Gray
    }

    val distance = if (userLat != null && userLon != null &&
        presence.liveLocationEnabled && presence.lat != 0.0 && presence.lon != 0.0
    ) {
        val results = FloatArray(1)
        Location.distanceBetween(userLat, userLon, presence.lat, presence.lon, results)
        results[0]
    } else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        // Avatar with safety-color border
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

        // Name
        Text(
            presence.name.split(" ").first(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        // Status note
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

        // Distance
        if (distance != null) {
            Text(
                text = if (distance < 1000) "${"%.0f".format(distance)}m"
                       else "${"%.1f".format(distance / 1000)}km",
                style = MaterialTheme.typography.labelSmall,
                color = borderColor,
                fontSize = 10.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Latest Alert Banner — single most recent alert above SOS (auto-hides >10min)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun LatestAlertBanner(
    latestAlert: SosAlert?,
    userLat: Double?,
    userLon: Double?,
    onViewAll: () -> Unit = {}
) {
    if (latestAlert == null) return

    // Auto-hide if older than 10 minutes
    val isRecent = remember(latestAlert.timestamp) {
        (System.currentTimeMillis() - latestAlert.timestamp) < 10 * 60 * 1000
    }
    if (!isRecent) return

    val context = LocalContext.current
    val distance = if (userLat != null && userLon != null &&
        latestAlert.lat != 0.0 && latestAlert.lon != 0.0
    ) {
        val results = FloatArray(1)
        Location.distanceBetween(userLat, userLon, latestAlert.lat, latestAlert.lon, results)
        results[0]
    } else null

    val accentColor = if (latestAlert.type == "SOS") Color(0xFFF44336) else Color(0xFFFF9800)
    val emoji = if (latestAlert.type == "SOS") "🚨" else "⚠️"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${latestAlert.name} needs help",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (distance != null) {
                    Text(
                        "${"%.0f".format(distance)}m away",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            // Navigate button
            if (latestAlert.lat != 0.0 && latestAlert.lon != 0.0) {
                FilledTonalButton(
                    onClick = {
                        val uri = Uri.parse("google.navigation:q=${latestAlert.lat},${latestAlert.lon}&mode=w")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {
                            val webUri = Uri.parse("https://maps.google.com/?q=${latestAlert.lat},${latestAlert.lon}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("View", fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Nearby Helpers Section — users within 1km with live location enabled
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NearbyHelpersSection(
    presenceList: List<UserPresence>,
    userLat: Double?,
    userLon: Double?
) {
    val nearbyHelpers = remember(presenceList, userLat, userLon) {
        if (userLat == null || userLon == null) emptyList()
        else presenceList.filter { p ->
            p.liveLocationEnabled && p.lat != 0.0 && p.lon != 0.0
        }.mapNotNull { p ->
            val results = FloatArray(1)
            Location.distanceBetween(userLat, userLon, p.lat, p.lon, results)
            if (results[0] <= 1000f) p to results[0] else null
        }.sortedBy { it.second }
    }

    if (nearbyHelpers.isEmpty()) return

    Column {
        Text(
            "🛡️ Nearby Helpers (${nearbyHelpers.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        nearbyHelpers.forEach { (presence, distance) ->
            NearbyHelperCard(presence, distance)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SOS Alert Card with Navigate button
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SosAlertCardWithNav(
    alert: SosAlert,
    userLat: Double?,
    userLon: Double?
) {
    val context = LocalContext.current
    val distance = if (userLat != null && userLon != null && alert.lat != 0.0 && alert.lon != 0.0) {
        val results = FloatArray(1)
        Location.distanceBetween(userLat, userLon, alert.lat, alert.lon, results)
        results[0]
    } else null

    val timeAgo = remember(alert.timestamp) {
        val diff = System.currentTimeMillis() - alert.timestamp
        val minutes = diff / (60 * 1000)
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F0)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Red indicator
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF44336)),
                contentAlignment = Alignment.Center
            ) {
                Text("🚨", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${alert.name} needs help!",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    Text(
                        timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (distance != null) {
                        Text(
                            " • ${"%.0f".format(distance)}m away",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            // Navigate button
            if (alert.lat != 0.0 && alert.lon != 0.0) {
                FilledTonalButton(
                    onClick = {
                        val uri = Uri.parse("google.navigation:q=${alert.lat},${alert.lon}&mode=w")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {
                            // Fallback to browser
                            val webUri = Uri.parse("https://maps.google.com/?q=${alert.lat},${alert.lon}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF44336),
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

// ═══════════════════════════════════════════════════════════════════════════
// Nearby Helper Card
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun NearbyHelperCard(presence: UserPresence, distance: Float) {
    val safetyColor = when (presence.safetyStateEnum) {
        SafetyState.SAFE -> Color(0xFF4CAF50)
        SafetyState.OUTSIDE -> Color(0xFFFF9800)
        SafetyState.SOS -> Color(0xFFF44336)
        SafetyState.OFFLINE -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with state dot
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(safetyColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        presence.name.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = safetyColor
                    )
                }
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(safetyColor)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(presence.name, fontWeight = FontWeight.SemiBold)
                if (presence.status.isNotBlank()) {
                    Text(
                        "\"${presence.status}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Distance badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = safetyColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (distance < 1000) "${"%.0f".format(distance)}m"
                           else "${"%.1f".format(distance / 1000)}km",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = safetyColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

