package com.example.womencentricnetwork.View

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.womencentricnetwork.Model.AppDatabase
import com.example.womencentricnetwork.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch

class MapFragment : Fragment(), OnMapReadyCallback {

    private val LOCATION_PERMISSION_CODE = 100
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val apiKey = "AIzaSyDU8a5yJ82Zoo7l7anFKDsEb5hTbNDCGpY"

//    private val placeTypes = listOf(
//        "police",
//        "hindu_temple",
//        "bus_station",
//        "train_station",
//        "subway_station",
//        "transit_station",
//        "cafe",
//        "restaurant",
//        "shopping_mall",
//        "supermarket",
//        "hospital",
//        "pharmacy",
//        "movie_theater",
//        "park",
//        "church",
//        "mosque"
//    )

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
            getCurrentLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Shows HeatMap of traffic
        googleMap.isTrafficEnabled = true

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }

        getCurrentLocation()
        Log.d("LOCATION","${getCurrentLocation()}")
    }

    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude


                    val myLoc = LatLng(lat, lon)

                    if (::mMap.isInitialized) {
                        mMap.addMarker(MarkerOptions().position(myLoc).title("You are here"))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, 16f))
                    }

                }
            }
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(com.example.womencentricnetwork.R.id.map) as? SupportMapFragment

        mapFragment?.getMapAsync(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
