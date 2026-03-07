package com.example.womencentricnetwork.View

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.Incident
import com.example.womencentricnetwork.Model.SafetyState
import com.example.womencentricnetwork.Model.UserPresence
import com.example.womencentricnetwork.R
import com.example.womencentricnetwork.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.ListenerRegistration

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapFragment"
        private const val LOCATION_PERMISSION_CODE = 100
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val firestoreManager by lazy { FirestoreManager() }
    private var incidentListener: ListenerRegistration? = null
    private var liveLocationListener: ListenerRegistration? = null
    private var presenceListener: ListenerRegistration? = null

    // Keep track of incident markers so we can clear and redraw on updates
    private val incidentMarkers = mutableListOf<Marker>()

    // Keep track of live SOS user markers
    private val liveLocationMarkers = mutableMapOf<String, Marker>()

    // Keep track of presence markers (safety-state colored)
    private val presenceMarkers = mutableMapOf<String, Marker>()

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        checkLocationPermission()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as? SupportMapFragment

        mapFragment?.getMapAsync(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove Firestore listeners to prevent memory leaks
        incidentListener?.remove()
        incidentListener = null
        liveLocationListener?.remove()
        liveLocationListener = null
        presenceListener?.remove()
        presenceListener = null
        _binding = null
    }

    // ── Map Ready ───────────────────────────────────────────────────────

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        googleMap.isTrafficEnabled = true

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }

        // Show user location with blue marker
        showCurrentLocation()

        // Start real-time incident listener
        startIncidentListener()

        // Start live SOS location listener
        startLiveLocationListener()

        // Start presence listener (safety-state colored markers)
        startPresenceListener()
    }

    // ── Location Permission ─────────────────────────────────────────────

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        } else {
            showCurrentLocation()
        }
    }

    // ── Show Current User Location (Blue Marker) ────────────────────────

    private fun showCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && ::mMap.isInitialized) {
                val myLoc = LatLng(location.latitude, location.longitude)

                mMap.addMarker(
                    MarkerOptions()
                        .position(myLoc)
                        .title("You are here")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, 15f))
            }
        }
    }

    // ── Real-time Incident Listener ─────────────────────────────────────

    private fun startIncidentListener() {
        incidentListener = firestoreManager.listenForIncidents { incidents ->
            if (!::mMap.isInitialized) return@listenForIncidents

            // Run on main thread (Firestore listener already calls on main thread)
            updateIncidentMarkers(incidents)
        }
    }

    // ── Update Incident Markers on Map ──────────────────────────────────

    private fun updateIncidentMarkers(incidents: List<Incident>) {
        // Clear previous incident markers
        for (marker in incidentMarkers) {
            marker.remove()
        }
        incidentMarkers.clear()

        // Handle empty state
        if (incidents.isEmpty()) {
            Toast.makeText(context, "No incident reports nearby", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "No incidents found in Firestore")
            return
        }

        Log.d(TAG, "Loaded ${incidents.size} incident(s) from Firestore")

        // Add red markers for each incident
        for (incident in incidents) {
            val position = LatLng(incident.latitude, incident.longitude)

            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Unsafe Area Report")
                    .snippet(incident.description)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            if (marker != null) {
                incidentMarkers.add(marker)
            }
        }
    }

    // ── Real-time Live SOS Location Listener ─────────────────────────────

    /**
     * Listen for all active SOS live locations from Firestore.
     * Shows orange markers for users currently sharing their live location.
     */
    private fun startLiveLocationListener() {
        liveLocationListener = firestoreManager.listenForActiveLiveLocations { locations ->
            if (!::mMap.isInitialized) return@listenForActiveLiveLocations

            Log.d(TAG, "Live locations update: ${locations.size} active SOS user(s)")

            // Remove markers for users no longer active
            val activeUids = locations.mapNotNull { it["uid"] as? String }.toSet()
            val toRemove = liveLocationMarkers.keys - activeUids
            for (uid in toRemove) {
                liveLocationMarkers[uid]?.remove()
                liveLocationMarkers.remove(uid)
            }

            // Update/add markers for active SOS users
            for (locData in locations) {
                val uid = locData["uid"] as? String ?: continue
                val lat = locData["lat"] as? Double ?: continue
                val lon = locData["lon"] as? Double ?: continue
                val accuracy = (locData["accuracy"] as? Double)?.toInt() ?: 0
                val position = LatLng(lat, lon)

                val existingMarker = liveLocationMarkers[uid]
                if (existingMarker != null) {
                    // Update position of existing marker
                    existingMarker.position = position
                    existingMarker.snippet = "Accuracy: ${accuracy}m"
                } else {
                    // Create new marker (orange for live SOS)
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("🚨 SOS — Live Location")
                            .snippet("Accuracy: ${accuracy}m")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    )
                    if (marker != null) {
                        liveLocationMarkers[uid] = marker
                    }
                }
            }
        }
    }

    // ── Real-time Presence Listener (Safety-State Colored Markers) ────────

    /**
     * Listen for user presence documents from Firestore.
     * Displays markers colored by safety state:
     *   Blue   → current user
     *   Green  → SAFE users
     *   Orange → OUTSIDE users
     *   Red    → SOS users
     *   (OFFLINE users are not shown)
     */
    private fun startPresenceListener() {
        presenceListener = firestoreManager.listenForPresence { presenceList ->
            if (!::mMap.isInitialized) return@listenForPresence

            Log.d(TAG, "Presence update: ${presenceList.size} user(s)")

            // Remove markers for users no longer present
            val activeUids = presenceList.map { it.uid }.toSet()
            val toRemove = presenceMarkers.keys - activeUids
            for (uid in toRemove) {
                presenceMarkers[uid]?.remove()
                presenceMarkers.remove(uid)
            }

            for (presence in presenceList) {
                // Skip users without location
                if (presence.lat == 0.0 && presence.lon == 0.0) continue
                // Skip OFFLINE users
                if (presence.safetyStateEnum == SafetyState.OFFLINE) continue

                val position = LatLng(presence.lat, presence.lon)
                val markerColor = when (presence.safetyStateEnum) {
                    SafetyState.SAFE -> BitmapDescriptorFactory.HUE_GREEN
                    SafetyState.OUTSIDE -> BitmapDescriptorFactory.HUE_YELLOW
                    SafetyState.SOS -> BitmapDescriptorFactory.HUE_RED
                    SafetyState.OFFLINE -> continue // already filtered
                }
                val stateEmoji = when (presence.safetyStateEnum) {
                    SafetyState.SAFE -> "🟢"
                    SafetyState.OUTSIDE -> "🟠"
                    SafetyState.SOS -> "🔴"
                    SafetyState.OFFLINE -> "⚫"
                }

                val snippet = buildString {
                    append("$stateEmoji ${presence.safetyState}")
                    if (presence.status.isNotBlank()) append(" — ${presence.status}")
                }

                val existing = presenceMarkers[presence.uid]
                if (existing != null) {
                    existing.position = position
                    existing.snippet = snippet
                    existing.title = presence.name
                } else {
                    val marker = mMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(presence.name)
                            .snippet(snippet)
                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                    )
                    if (marker != null) {
                        presenceMarkers[presence.uid] = marker
                    }
                }
            }
        }
    }
}
