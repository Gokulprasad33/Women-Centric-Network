package com.example.womencentricnetwork.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.example.womencentricnetwork.databinding.FragmentHomeBinding
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val emergencyContactDao by lazy { db.emergencyContactDao() }
    private val sosEventDao by lazy { db.sosEventDao() }
    private val settingsPrefs by lazy { SettingsPreferencesDataStore(requireContext()) }
    private val firestoreManager by lazy { FirestoreManager() }

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

//        view.findViewById<ImageView>(R.id.navHome).setOnClickListener {
//            navController.navigate(R.id.homeFragment)
//        }

        binding.navChat.setOnClickListener {
            navController.navigate(R.id.chatFragment)
        }

        binding.navProfile.setOnClickListener {
            navController.navigate(R.id.profileFragment)
        }

        binding.navCommunity.setOnClickListener {
            navController.navigate(R.id.communityFragment)
        }

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
            openPlacesInMaps()
        }

        binding.btnReportLocation.setOnClickListener {
            reportLocation()
        }

        binding.btnSOS.setOnClickListener {
            sos()
        }

        // ── Firebase: Test connection & register FCM token ──────────
        testFirebaseConnection()
        registerFcmToken()

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

                // Show confirmation
                Toast.makeText(
                    context,
                    "SOS alert sent to $sentCount of ${contacts.size} contact(s)",
                    Toast.LENGTH_LONG
                ).show()

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

    private fun openPlacesInMaps() {

        val query = "police OR hospital OR clinic OR pharmacy OR bus OR train OR metro OR transit OR market OR mall OR store OR tea shop OR bakery OR restaurant OR hotel OR kovil OR church OR mosque OR petrol pump OR beach"

        val uri = Uri.parse("geo:0,0?q=$query")

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        startActivity(intent)
    }

}
