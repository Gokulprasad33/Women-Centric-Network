package com.example.womencentricnetwork.View

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app OpenStreetMap screen showing the walking route from
 * the user's location to the nearest safe place.
 *
 * Uses osmdroid for the map and OSRM for the walking route.
 *
 * Expected arguments (Bundle):
 *   - placeName   : String
 *   - placeType   : String
 *   - placeLat    : Double
 *   - placeLng    : Double
 *   - distanceM   : Float
 *   - userLat     : Double
 *   - userLng     : Double
 */
class SafeRouteMapFragment : Fragment() {

    companion object {
        private const val TAG = "SafeRouteMap"
    }

    private lateinit var mapView: MapView
    private lateinit var tvPlaceName: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvWalkTime: TextView
    private lateinit var tvPlaceType: TextView

    private var placeLat = 0.0
    private var placeLng = 0.0
    private var userLat = 0.0
    private var userLng = 0.0
    private var placeName = "Safe Place"
    private var placeType = ""
    private var distanceM = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Configure osmdroid
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
        }

        // Read arguments
        arguments?.let {
            placeName = it.getString("placeName", "Safe Place")
            placeType = it.getString("placeType", "")
            placeLat = it.getDouble("placeLat", 0.0)
            placeLng = it.getDouble("placeLng", 0.0)
            userLat = it.getDouble("userLat", 0.0)
            userLng = it.getDouble("userLng", 0.0)
            distanceM = it.getFloat("distanceM", 0f)
        }

        // Build layout programmatically (no XML needed)
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── Info panel ──────────────────────────────────────────────
        val infoPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
            elevation = 8f
        }

        tvPlaceName = TextView(requireContext()).apply {
            text = placeName
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        infoPanel.addView(tvPlaceName)

        tvPlaceType = TextView(requireContext()).apply {
            text = placeType.replace("_", " ").replaceFirstChar { it.uppercase() }
            textSize = 13f
            setTextColor(Color.GRAY)
        }
        infoPanel.addView(tvPlaceType)

        tvDistance = TextView(requireContext()).apply {
            text = formatDistance(distanceM)
            textSize = 15f
            setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, dp(4), 0, 0)
        }
        infoPanel.addView(tvDistance)

        tvWalkTime = TextView(requireContext()).apply {
            text = "Calculating route..."
            textSize = 14f
            setTextColor(Color.parseColor("#388E3C"))
            setPadding(0, dp(2), 0, 0)
        }
        infoPanel.addView(tvWalkTime)

        root.addView(infoPanel)

        // ── Map ─────────────────────────────────────────────────────
        mapView = MapView(requireContext()).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(mapView)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        fetchAndDrawRoute()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // ── Map setup ───────────────────────────────────────────────────────

    private fun setupMap() {
        val userPoint = GeoPoint(userLat, userLng)
        val destPoint = GeoPoint(placeLat, placeLng)

        // User marker (blue)
        val userMarker = Marker(mapView).apply {
            position = userPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "You are here"
            snippet = "${"%.6f".format(userLat)}, ${"%.6f".format(userLng)}"
        }
        // Use tint: blue circle
        try {
            val drawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mylocation)
            drawable?.setTint(Color.BLUE)
            userMarker.icon = drawable
        } catch (_: Exception) { /* default icon */ }

        // Destination marker (green)
        val destMarker = Marker(mapView).apply {
            position = destPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = placeName
            snippet = placeType.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
        try {
            val drawable = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_myplaces)
            drawable?.setTint(Color.parseColor("#388E3C"))
            destMarker.icon = drawable
        } catch (_: Exception) { /* default icon */ }

        mapView.overlays.add(userMarker)
        mapView.overlays.add(destMarker)

        // Zoom to show both points
        val box = BoundingBox.fromGeoPointsSafe(listOf(userPoint, destPoint))
        mapView.post {
            try {
                mapView.zoomToBoundingBox(box.increaseByScale(1.4f), true)
            } catch (_: Exception) {
                mapView.controller.setCenter(userPoint)
                mapView.controller.setZoom(15.0)
            }
        }
    }

    // ── OSRM routing ────────────────────────────────────────────────────

    private fun fetchAndDrawRoute() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val routeResult = fetchOsrmRoute(userLat, userLng, placeLat, placeLng)
                if (routeResult != null) {
                    val (points, durationSec, distMetres) = routeResult
                    withContext(Dispatchers.Main) {
                        drawRoute(points)
                        tvDistance.text = formatDistance(distMetres)
                        tvWalkTime.text = "🚶 ${formatDuration(durationSec)} walking"
                    }
                } else {
                    // Route failed — try snapping destination to nearest road
                    Log.w(TAG, "OSRM route failed, attempting road snap")
                    val snapped = snapToNearestRoad(placeLat, placeLng)
                    if (snapped != null) {
                        val retryResult = fetchOsrmRoute(userLat, userLng, snapped.first, snapped.second)
                        if (retryResult != null) {
                            val (pts, dur, dist) = retryResult
                            withContext(Dispatchers.Main) {
                                drawRoute(pts)
                                tvDistance.text = formatDistance(dist)
                                tvWalkTime.text = "🚶 ${formatDuration(dur)} walking (via road)"
                            }
                            return@launch
                        }
                    }
                    // Draw straight line fallback
                    withContext(Dispatchers.Main) {
                        drawStraightLine()
                        tvWalkTime.text = "🚶 ~${formatDuration((distanceM / 1.2).toLong())} walking (estimated)"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Route fetch error", e)
                withContext(Dispatchers.Main) {
                    drawStraightLine()
                    tvWalkTime.text = "🚶 ~${formatDuration((distanceM / 1.2).toLong())} walking (estimated)"
                }
            }
        }
    }

    /**
     * Calls OSRM foot routing API.
     * Returns Triple(geopoints, durationSeconds, distanceMetres) or null.
     */
    private fun fetchOsrmRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Triple<List<GeoPoint>, Long, Float>? {
        val urlStr = "https://router.project-osrm.org/route/v1/foot/" +
                "$fromLon,$fromLat;$toLon,$toLat" +
                "?overview=full&geometries=geojson"

        Log.d(TAG, "OSRM request: $urlStr")

        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "WomenCentricNetwork/1.0")

        try {
            if (conn.responseCode != 200) {
                Log.e(TAG, "OSRM HTTP ${conn.responseCode}")
                return null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val json = JSONObject(body)

            if (json.optString("code") != "Ok") {
                Log.e(TAG, "OSRM code: ${json.optString("code")}")
                return null
            }

            val route = json.getJSONArray("routes").getJSONObject(0)
            val duration = route.optLong("duration", 0)
            val distance = route.optDouble("distance", 0.0).toFloat()

            val geometry = route.getJSONObject("geometry")
            val coords = geometry.getJSONArray("coordinates")

            val points = mutableListOf<GeoPoint>()
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                val lon = c.getDouble(0)
                val lat = c.getDouble(1)
                points.add(GeoPoint(lat, lon))
            }

            Log.d(TAG, "OSRM route: ${points.size} points, ${"%.0f".format(distance)}m, ${duration}s")
            return Triple(points, duration, distance)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Snap a point to the nearest road using OSRM's nearest service.
     * Returns Pair(lat, lon) or null.
     */
    private fun snapToNearestRoad(lat: Double, lon: Double): Pair<Double, Double>? {
        val urlStr = "https://router.project-osrm.org/nearest/v1/foot/$lon,$lat"
        Log.d(TAG, "OSRM nearest: $urlStr")

        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "WomenCentricNetwork/1.0")

        try {
            if (conn.responseCode != 200) return null

            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") return null

            val waypoints = json.getJSONArray("waypoints")
            if (waypoints.length() == 0) return null

            val location = waypoints.getJSONObject(0).getJSONArray("location")
            val snappedLon = location.getDouble(0)
            val snappedLat = location.getDouble(1)
            Log.d(TAG, "Snapped to road: $snappedLat, $snappedLon")
            return snappedLat to snappedLon
        } catch (e: Exception) {
            Log.e(TAG, "Road snap failed", e)
            return null
        } finally {
            conn.disconnect()
        }
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    private fun drawRoute(points: List<GeoPoint>) {
        if (points.isEmpty()) return

        val polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#1976D2")  // blue
            outlinePaint.strokeWidth = 10f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
        }

        mapView.overlays.add(0, polyline) // behind markers
        mapView.invalidate()

        // Re-zoom to include the route
        try {
            val box = BoundingBox.fromGeoPointsSafe(points)
            mapView.post { mapView.zoomToBoundingBox(box.increaseByScale(1.3f), true) }
        } catch (_: Exception) { }
    }

    private fun drawStraightLine() {
        val points = listOf(
            GeoPoint(userLat, userLng),
            GeoPoint(placeLat, placeLng)
        )
        val polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#90CAF9") // light blue dashed
            outlinePaint.strokeWidth = 6f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f)
        }
        mapView.overlays.add(0, polyline)
        mapView.invalidate()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun formatDistance(metres: Float): String {
        return if (metres < 1000) "${metres.toInt()} m"
        else "${"%.1f".format(metres / 1000)} km"
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        return when {
            mins < 1 -> "< 1 min"
            mins < 60 -> "$mins min"
            else -> "${mins / 60}h ${mins % 60}min"
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

