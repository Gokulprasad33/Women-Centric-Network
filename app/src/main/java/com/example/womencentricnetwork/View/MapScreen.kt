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

    // Keep track of incident markers so we can clear and redraw on updates
    private val incidentMarkers = mutableListOf<Marker>()

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
        // Remove Firestore listener to prevent memory leaks
        incidentListener?.remove()
        incidentListener = null
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
}
