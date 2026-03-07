package com.example.womencentricnetwork.View

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment

/**
 * Displays the nearest safe place result and lets the user
 * launch Google Maps turn-by-turn navigation to it.
 *
 * Expected arguments (Bundle):
 *   - placeName   : String
 *   - placeType   : String
 *   - placeLat    : Double
 *   - placeLng    : Double
 *   - distanceM   : Float
 */
class SafeRouteFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val placeName = arguments?.getString("placeName") ?: "Unknown"
        val placeType = arguments?.getString("placeType") ?: ""
        val placeLat = arguments?.getDouble("placeLat") ?: 0.0
        val placeLng = arguments?.getDouble("placeLng") ?: 0.0
        val distanceM = arguments?.getFloat("distanceM") ?: 0f

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    SafeRouteScreen(
                        placeName = placeName,
                        placeType = placeType,
                        placeLat = placeLat,
                        placeLng = placeLng,
                        distanceMeters = distanceM,
                        onNavigateClick = { lat, lng, name ->
                            openGoogleMapsNavigation(lat, lng, name)
                        }
                    )
                }
            }
        }
    }

    private fun openGoogleMapsNavigation(lat: Double, lng: Double, name: String) {
        val encodedName = Uri.encode(name)
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=w")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        // Fallback: if Google Maps isn't installed, use browser
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val browserUri = Uri.parse("https://maps.google.com/?q=$lat,$lng($encodedName)")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Compose UI
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun SafeRouteScreen(
    placeName: String,
    placeType: String,
    placeLat: Double,
    placeLng: Double,
    distanceMeters: Float,
    onNavigateClick: (lat: Double, lng: Double, name: String) -> Unit
) {
    val distanceText = if (distanceMeters < 1000) {
        "${distanceMeters.toInt()} m away"
    } else {
        "${"%.1f".format(distanceMeters / 1000)} km away"
    }

    val typeLabel = placeType.replace("_", " ")
        .replaceFirstChar { it.uppercase() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Nearest Safe Place",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = placeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (typeLabel.isNotBlank() && typeLabel != "Unknown") {
                        AssistChip(
                            onClick = {},
                            label = { Text(typeLabel) }
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "📍 ${"%.6f".format(placeLat)}, ${"%.6f".format(placeLng)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Navigate button
            Button(
                onClick = { onNavigateClick(placeLat, placeLng, placeName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Start Navigation",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(12.dp))

            // Open in Maps (fallback)
            OutlinedButton(
                onClick = {
                    onNavigateClick(placeLat, placeLng, placeName)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open in Google Maps")
            }
        }
    }
}

