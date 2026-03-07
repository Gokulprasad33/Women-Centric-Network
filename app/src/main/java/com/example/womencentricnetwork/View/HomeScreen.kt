package com.example.womencentricnetwork.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.AppDatabase
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SettingsPreferencesDataStore
import com.example.womencentricnetwork.Model.Settings.SosEventEntity
import com.example.womencentricnetwork.R
import com.example.womencentricnetwork.Repository.SafePlaceRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.womencentricnetwork.databinding.FragmentHomeBinding

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val emergencyContactDao by lazy { db.emergencyContactDao() }
    private val sosEventDao by lazy { db.sosEventDao() }
    private val settingsPrefs by lazy { SettingsPreferencesDataStore(requireContext()) }
    private val firestoreManager by lazy { FirestoreManager() }
    private val safePlaceRepository by lazy { SafePlaceRepository(requireContext()) }
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    // ── Live SOS Location Tracking ───────────────────────────────────────
    private var isSosActive = false
    private var liveLocationCallback: LocationCallback? = null
    private val sosTimeoutHandler = Handler(Looper.getMainLooper())
    private val SOS_TIMEOUT_MS = 10L * 60 * 1000  // 10 minutes auto-stop
    private val sosTimeoutRunnable = Runnable { stopLiveLocationTracking() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Request SMS and CALL_PHONE permissions upfront
        val missingPermissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.SEND_SMS)
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CALL_PHONE)
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 200)
        }


        val navController = findNavController()


        binding.navNotification.setOnClickListener {
            navController.navigate(R.id.notificationFragment)
        }

        binding.navSettings.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
        }

        binding.navMap.setOnClickListener {
            navController.navigate(R.id.mapFragment)
        }
        binding.btnShareLocation.setOnClickListener {
            fetchLocation { lat, lon ->
                shareLocation(lat,lon)
            }
        }
        binding.btnSafeplace.setOnClickListener {
            findSafeSpace()
        }

        binding.btnReportLocation.setOnClickListener {
            reportLocation()
        }

        binding.btnSOS.setOnClickListener {
            sos()
        }

        binding.btnStopSOS.setOnClickListener {
            stopLiveLocationTracking()
        }

        // ── Firebase: Test connection & register FCM token ──────────
        testFirebaseConnection()
        registerFcmToken()

        // ── Preload safe places dataset into memory ─────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                safePlaceRepository.preload()
            }
        }
        }


    // ── Core SOS Flow ────────────────────────────────────────────────────

    private fun sos() {
        // 1. Check location permission
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            Toast.makeText(context, "Location permission required for SOS", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Check SMS permission
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 200)
            Toast.makeText(context, "SMS permission required for SOS", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "SOS activated! Fetching location...", Toast.LENGTH_SHORT).show()

        // 3. Fetch location → send alerts → save event → call emergency
        fetchLocation { lat, lon ->
            viewLifecycleOwner.lifecycleScope.launch {
                // Fetch emergency contacts
                val contacts: List<EmergencyContactEntity> = emergencyContactDao.getAll().first()

                if (contacts.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No emergency contacts saved. Please add contacts in Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // Build SOS message with location link
                val locationLink = "https://maps.google.com/?q=$lat,$lon"
                val message = "Emergency Alert!\n\n" +
                        "I may be in danger. My current location is:\n" +
                        "$locationLink\n\n" +
                        "Please reach me immediately."

                // Send SMS to all emergency contacts
                val smsManager = SmsManager.getDefault()
                var sentCount = 0

                for (contact in contacts) {
                    try {
                        // Split into parts if message exceeds single SMS limit
                        val parts = smsManager.divideMessage(message)
                        smsManager.sendMultipartTextMessage(
                            contact.phoneNumber, null, parts, null, null
                        )
                        sentCount++
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Failed to send SMS to ${contact.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Save SOS event to database
                val sosEvent: SosEventEntity
                try {
                    sosEvent = SosEventEntity(
                        latitude = lat,
                        longitude = lon,
                        message = message
                    )
                    sosEventDao.insertEvent(sosEvent)
                } catch (e: Exception) {
                    // Log but don't block the flow
                    Toast.makeText(
                        context,
                        "SOS alert sent to $sentCount of ${contacts.size} contact(s)",
                        Toast.LENGTH_LONG
                    ).show()
                    callEmergencyNumber()
                    return@launch
                }

                // Sync SOS event to Firestore
                try {
                    firestoreManager.saveSosEvent(sosEvent)
                    firestoreManager.updateLastLocation(lat, lon)
                } catch (e: Exception) {
                    Log.e("HomeFragment", "Firestore sync failed: ${e.message}")
                }

                // Broadcast in-app SOS alert & update presence to SOS
                try {
                    firestoreManager.createSosAlert(lat, lon)
                    firestoreManager.updateSafetyState(com.example.womencentricnetwork.Model.SafetyState.SOS)
                } catch (e: Exception) {
                    Log.e("HomeFragment", "SOS alert broadcast failed: ${e.message}")
                }

                // Show confirmation
                Toast.makeText(
                    context,
                    "SOS alert sent to $sentCount of ${contacts.size} contact(s)",
                    Toast.LENGTH_LONG
                ).show()

                // Start continuous live location tracking
                startLiveLocationTracking()

                // Auto-call emergency number (112)
                callEmergencyNumber()
            }
        }
    }

    // ── Fetch GPS Location ──────────────────────────────────────────────

    private fun fetchLocation(onResult: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }

        val fused = LocationServices.getFusedLocationProviderClient(requireContext())

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            0
        ).setMaxUpdates(1).build()

        fused.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    onResult(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(
                        context,
                        "Unable to get GPS location. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                fused.removeLocationUpdates(this)
            }
        }, Looper.getMainLooper())
    }

    // ── Auto-call Emergency Number ──────────────────────────────────────

    private fun callEmergencyNumber() {
        val emergencyNumber = "112"
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$emergencyNumber")
                }
                startActivity(callIntent)
            } catch (e: Exception) {
                // Fallback: open dialer if ACTION_CALL fails
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$emergencyNumber")
                }
                startActivity(dialIntent)
            }
        } else {
            // No CALL_PHONE permission → open dialer instead
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$emergencyNumber")
            }
            startActivity(dialIntent)
        }
    }

    // ── Live Location Tracking (SOS) ─────────────────────────────────────

    /**
     * Start continuous GPS updates every 5 seconds during SOS.
     * Uploads each update to Firestore liveLocations/{uid}.
     * Auto-stops after 10 minutes for battery safety.
     */
    private fun startLiveLocationTracking() {
        if (isSosActive) {
            Log.d("HomeFragment", "Live tracking already active")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("HomeFragment", "Cannot start live tracking — location permission missing")
            Toast.makeText(context, "Location permission required for live tracking", Toast.LENGTH_SHORT).show()
            return
        }

        isSosActive = true
        Log.d("HomeFragment", "Starting live location tracking")

        // Show Stop SOS button
        binding.btnStopSOS.visibility = View.VISIBLE

        // Build high-accuracy location request: update every 5 seconds
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000 // 5 seconds interval
        )
            .setMinUpdateIntervalMillis(3000)       // fastest: 3 seconds
            .setMinUpdateDistanceMeters(5f)          // only if moved > 5m
            .build()

        // Create callback that uploads each location to Firestore
        liveLocationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                Log.d(
                    "HomeFragment",
                    "Live location update: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)"
                )

                // Upload to Firestore (fire-and-forget, non-blocking)
                firestoreManager.updateLiveLocation(
                    lat = location.latitude,
                    lon = location.longitude,
                    accuracy = location.accuracy
                )
            }
        }

        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            liveLocationCallback!!,
            Looper.getMainLooper()
        )

        // Schedule auto-stop after 10 minutes
        sosTimeoutHandler.postDelayed(sosTimeoutRunnable, SOS_TIMEOUT_MS)

        Toast.makeText(context, "Live location tracking started", Toast.LENGTH_SHORT).show()
    }

    /**
     * Stop continuous GPS tracking and mark live location as inactive in Firestore.
     */
    private fun stopLiveLocationTracking() {
        if (!isSosActive) {
            Log.d("HomeFragment", "Live tracking not active — nothing to stop")
            return
        }

        isSosActive = false
        Log.d("HomeFragment", "Stopping live location tracking")

        // Remove location updates
        liveLocationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        liveLocationCallback = null

        // Cancel auto-stop timer
        sosTimeoutHandler.removeCallbacks(sosTimeoutRunnable)

        // Hide Stop SOS button
        if (_binding != null) {
            binding.btnStopSOS.visibility = View.GONE
        }

        // Mark live location as inactive in Firestore + reset presence
        viewLifecycleOwner.lifecycleScope.launch {
            firestoreManager.clearLiveLocation()
            firestoreManager.updateSafetyState(com.example.womencentricnetwork.Model.SafetyState.SAFE)
        }

        Toast.makeText(context, "SOS tracking stopped", Toast.LENGTH_SHORT).show()
    }

    // ── Firebase: Test Connection ─────────────────────────────────────

    private fun testFirebaseConnection() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = firestoreManager.testConnection()
            result.onSuccess { docId ->
                Log.d("HomeFragment", "✅ Firebase connected — test doc: $docId")
            }
            result.onFailure { error ->
                Log.e("HomeFragment", "❌ Firebase test failed: ${error.message}")
            }
        }
    }

    // ── Firebase: Register FCM Token ────────────────────────────────────

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("HomeFragment", "FCM Token: $token")

                // Save to Firestore if user is logged in
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        firestoreManager.saveFcmToken(token)
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "FCM token save failed: ${e.message}")
                    }
                }
            } else {
                Log.e("HomeFragment", "FCM token retrieval failed", task.exception)
            }
        }
    }

    // ── Share Location (manual button) ──────────────────────────────────

    private fun shareLocation(lat: Double, lon: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Fetch emergency contacts from Room
            val contacts: List<EmergencyContactEntity> = emergencyContactDao.getAll().first()

            if (contacts.isEmpty()) {
                Toast.makeText(context, "No emergency contacts. Add them in Settings.", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Fetch custom SOS message from DataStore
            val prefs = settingsPrefs.settingsFlow.first()
            val locationSharingEnabled = prefs.locationSharingEnabled
            val sosMessage = prefs.sosMessage

            val message = if (locationSharingEnabled) {
                "$sosMessage https://maps.google.com/?q=$lat,$lon"
            } else {
                sosMessage
            }

            val smsManager = SmsManager.getDefault()

            for (contact in contacts) {
                try {
                    smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to send SMS to ${contact.name}", Toast.LENGTH_SHORT).show()
                }
            }

            Toast.makeText(context, "SOS SMS sent to ${contacts.size} contact(s)", Toast.LENGTH_LONG).show()
        }
    }
    private fun reportLocation() {
        fetchLocation { lat, lon ->
            viewLifecycleOwner.lifecycleScope.launch {
                val result = firestoreManager.submitIncidentReport(
                    latitude = lat,
                    longitude = lon,
                    description = "Unsafe location reported by user"
                )
                result.onSuccess {
                    Toast.makeText(context, "Location reported successfully", Toast.LENGTH_LONG).show()
                }
                result.onFailure { error ->
                    Log.e("HomeFragment", "Report failed: ${error.message}")
                    Toast.makeText(context, "Failed to report: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Find Safe Space (local dataset, no network) ───────────────────

    private fun findSafeSpace() {
        Log.d("HOME_SAFE", "findSafeSpace called. loaded=${safePlaceRepository.isLoaded} error=${safePlaceRepository.loadError}")

        // Ensure data is loaded
        if (!safePlaceRepository.isLoaded) {
            Toast.makeText(requireContext(), "Loading safe places...", Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                safePlaceRepository.preload()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (safePlaceRepository.isLoaded) {
                        findSafeSpace() // retry once loaded
                    } else {
                        Toast.makeText(requireContext(), "No safe places dataset loaded", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        // Permission check
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            Toast.makeText(context, "Location permission required", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(requireContext(), "Finding nearest safe place...", Toast.LENGTH_SHORT).show()

        // Primary: current high-accuracy fix
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d("HOME_SAFE", "Got currentLocation ${loc.latitude}, ${loc.longitude}")
                    handleSafePlaceResult(loc.latitude, loc.longitude)
                } else {
                    Log.w("HOME_SAFE", "currentLocation null, trying lastLocation")
                    // Fallback: lastLocation
                    fusedLocationClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            Log.d("HOME_SAFE", "Using lastLocation ${last.latitude}, ${last.longitude}")
                            handleSafePlaceResult(last.latitude, last.longitude)
                        } else {
                            Toast.makeText(requireContext(), "Unable to get location. Please enable GPS and try again.", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener { e ->
                        Log.e("HOME_SAFE", "lastLocation failed", e)
                        Toast.makeText(requireContext(), "Location error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HOME_SAFE", "currentLocation failed", e)
                Toast.makeText(requireContext(), "Location error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun handleSafePlaceResult(lat: Double, lon: Double) {
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val result = safePlaceRepository.findNearestWithDistance(lat, lon)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (result == null) {
                    Toast.makeText(requireContext(), "No safe places within 5 km", Toast.LENGTH_LONG).show()
                    return@withContext
                }

                val (place, distance) = result
                Log.d("HOME_SAFE", "Nearest: ${place.name} (${place.type}) at ${"%.0f".format(distance)}m")

                val bundle = Bundle().apply {
                    putString("placeName", place.name)
                    putString("placeType", place.type)
                    putDouble("placeLat", place.lat)
                    putDouble("placeLng", place.lng)
                    putFloat("distanceM", distance)
                    putDouble("userLat", lat)
                    putDouble("userLng", lon)
                }

                try {
                    findNavController().navigate(R.id.safeRouteFragment, bundle)
                } catch (e: Exception) {
                    Log.e("HOME_SAFE", "Navigation failed", e)
                    Toast.makeText(requireContext(), "Navigation error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPlacesInMaps() {

        val query = "police OR hospital OR clinic OR pharmacy OR bus OR train OR metro OR transit OR market OR mall OR store OR tea shop OR bakery OR restaurant OR hotel OR kovil OR church OR mosque OR petrol pump OR beach"

        val uri = Uri.parse("geo:0,0?q=$query")

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        startActivity(intent)
    }

    // ── Lifecycle cleanup ────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop live tracking to prevent leaks (but keep SOS active if running)
        liveLocationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        sosTimeoutHandler.removeCallbacks(sosTimeoutRunnable)
        _binding = null
    }

}
