package com.example.womencentricnetwork.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Model.AppDatabase
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SettingsPreferencesDataStore
import com.example.womencentricnetwork.R
import com.example.womencentricnetwork.databinding.FragmentHomeBinding
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val emergencyContactDao by lazy { db.emergencyContactDao() }
    private val settingsPrefs by lazy { SettingsPreferencesDataStore(requireContext()) }

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


        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 200)
            return
        }

        fun fetchLocation(onResult: (Double, Double) -> Unit) {
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
                    val loc = result.lastLocation ?: return
                    onResult(loc.latitude, loc.longitude)

                    fused.removeLocationUpdates(this) // stops after 1 reading
                }
            }, Looper.getMainLooper())
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

        }


    private fun sos(){
        // Call police

        // Share text to ngos and supportive volunteers

    }


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
    private fun reportLocation(){
        //Save it to db
        Toast.makeText(context,"Location reported",Toast.LENGTH_LONG).show()
    }

    private fun openPlacesInMaps() {

        val query = "police OR hospital OR clinic OR pharmacy OR bus OR train OR metro OR transit OR market OR mall OR store OR tea shop OR bakery OR restaurant OR hotel OR kovil OR church OR mosque OR petrol pump OR beach"

        val uri = Uri.parse("geo:0,0?q=$query")

        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")

        startActivity(intent)
    }

}
