package com.example.womencentricnetwork.Repository

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.womencentricnetwork.Model.SafePlace
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads safe places from local assets once, caches them, and provides
 * lightning-fast nearest lookup (offline only, radius-based).
 */
class SafePlaceRepository(private val context: Context) {

    companion object {
        private const val TAG = "SafePlaceRepo"

        /** Ordered list of asset paths to try. */
        private val FILE_CANDIDATES = listOf(
            "safeSpace/northTamilnadu.json",
            "safe_places_chennai.json",
            "safe_places.json",
            "safeplaces.json",
            "northTamilnadu.json"
        )

        /** Priority amenity types (public / busy places). */
        private val PRIORITY_TYPES = setOf(
            "police", "hospital", "clinic", "pharmacy", "fire_station",
            "mall", "bus_station", "railway_station", "fuel", "bank", "atm",
            "community_centre", "library", "restaurant", "cafe", "public_transport",
            "shopping_center", "metro", "subway_entrance", "college", "university"
        )

        /** Busy-place types for the expanded fallback tier. */
        private val BUSY_TYPES = setOf(
            "bus_station", "railway_station", "fuel", "mall", "college", "university"
        )

        private const val RADIUS_NEAR = 500f        // metres
        private const val RADIUS_EXPANDED = 2000f    // metres
        private const val RADIUS_MAX = 5000f         // metres — hard cap
    }

    // ── Public state ────────────────────────────────────────────────────

    val places: MutableList<SafePlace> = mutableListOf()

    var isLoaded: Boolean = false
        private set

    var loadError: String? = null
        private set

    // ── Preload ─────────────────────────────────────────────────────────

    /** Load & parse JSON from assets. Call on IO thread. */
    fun preload() {
        if (isLoaded && places.isNotEmpty()) {
            Log.d(TAG, "Already loaded ${places.size} places; skipping")
            return
        }

        try {
            val json = loadJsonFromAssets()
            if (json.isNullOrBlank()) {
                loadError = "No safe places dataset found in assets"
                Log.e(TAG, loadError!!)
                return
            }
            parseJson(json)
            if (places.isEmpty()) {
                loadError = "Parsed 0 places from dataset"
                Log.e(TAG, loadError!!)
            } else {
                isLoaded = true
                loadError = null
                Log.d(TAG, "Loaded ${places.size} places from assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error loading safe places", e)
            loadError = e.message
        }
    }

    // ── Asset loader ────────────────────────────────────────────────────

    private fun loadJsonFromAssets(): String? {
        for (name in FILE_CANDIDATES) {
            try {
                context.assets.open(name).use { stream ->
                    val text = BufferedReader(InputStreamReader(stream)).readText()
                    Log.d(TAG, "Found asset: $name (${text.length} chars)")
                    return text
                }
            } catch (_: Exception) { /* try next */ }
        }
        val topLevel = context.assets.list("")?.joinToString() ?: "none"
        val safeSpace = try { context.assets.list("safeSpace")?.joinToString() ?: "none" } catch (_: Exception) { "n/a" }
        Log.e(TAG, "Asset not found. Root assets: [$topLevel]  safeSpace/: [$safeSpace]")
        return null
    }

    // ── JSON parser (multi-format) ──────────────────────────────────────

    private fun parseJson(raw: String) {
        places.clear()
        val trimmed = raw.trim()

        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("elements") -> obj.getJSONArray("elements")
                    obj.has("features") -> obj.getJSONArray("features")
                    obj.has("places")   -> obj.getJSONArray("places")
                    obj.has("data")     -> obj.getJSONArray("data")
                    else -> JSONArray()
                }
            }
            else -> JSONArray()
        }

        var parsed = 0; var skipped = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val place = parsePlace(item)
            if (place != null) { places.add(place); parsed++ } else { skipped++ }
        }
        Log.d(TAG, "Parsed $parsed places, skipped $skipped")
    }

    private fun parsePlace(obj: JSONObject): SafePlace? {
        // ── OSM Overpass ────────────────────────────────────────────
        if (obj.has("lat") && obj.has("lon")) {
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null

            val tags = obj.optJSONObject("tags")
            val name = tags?.optString("name:en", "")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("name", "")
                ?: obj.optString("name", "")
            val type = tags?.optString("amenity", "")
                ?: tags?.optString("shop", "")
                ?: tags?.optString("tourism", "")
                ?: obj.optString("type", "unknown")

            if (name.isBlank()) return null
            val id = obj.optString("id", obj.optLong("id", System.nanoTime()).toString())
            return SafePlace(id, name, type.ifBlank { "unknown" }, lat, lon)
        }

        // ── GeoJSON ─────────────────────────────────────────────────
        if (obj.has("geometry")) {
            val coords = obj.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lon = coords?.optDouble(0, Double.NaN) ?: Double.NaN
            val lat = coords?.optDouble(1, Double.NaN) ?: Double.NaN
            val props = obj.optJSONObject("properties")
            val name = props?.optString("name", "") ?: ""
            val type = props?.optString("amenity", props?.optString("type", "unknown") ?: "unknown") ?: "unknown"
            if (lat.isNaN() || lon.isNaN() || name.isBlank()) return null
            val id = props?.optString("id", System.nanoTime().toString()) ?: System.nanoTime().toString()
            return SafePlace(id, name, type.ifBlank { "unknown" }, lat, lon)
        }

        // ── Simple { latitude, longitude } ──────────────────────────
        if (obj.has("latitude") || obj.has("lat")) {
            val lat = obj.optDouble("latitude", obj.optDouble("lat", Double.NaN))
            val lon = obj.optDouble("longitude", obj.optDouble("lng", Double.NaN))
            val name = obj.optString("name", "")
            val type = obj.optString("type", obj.optString("category", "unknown"))
            if (lat.isNaN() || lon.isNaN() || name.isBlank()) return null
            val id = obj.optString("id", System.nanoTime().toString())
            return SafePlace(id, name, type.ifBlank { "unknown" }, lat, lon)
        }

        return null
    }

    // ── Radius-based nearest search ────────────────────────────────────���

    /**
     * Tiered search:
     *  1. Any place within 500 m  → nearest
     *  2. Any place within 2 km   → nearest
     *  3. Busy place within 5 km  → nearest bus_station / railway / fuel / mall / college
     *  4. Any place within 5 km   → nearest
     *  5. null                     → nothing usable
     */
    fun findNearestWithDistance(userLat: Double, userLng: Double): Pair<SafePlace, Float>? {
        if (places.isEmpty()) return null

        data class Entry(val place: SafePlace, val dist: Float)

        val distArr = FloatArray(1)
        val all = ArrayList<Entry>(places.size)

        for (p in places) {
            Location.distanceBetween(userLat, userLng, p.lat, p.lng, distArr)
            all.add(Entry(p, distArr[0]))
        }

        // Tier 1: any ≤ 500 m
        val tier1 = all.filter { it.dist <= RADIUS_NEAR }.minByOrNull { it.dist }
        if (tier1 != null) {
            Log.d(TAG, "Tier1 (≤500m): ${tier1.place.name} @ ${"%.0f".format(tier1.dist)}m")
            return tier1.place to tier1.dist
        }

        // Tier 2: any ≤ 2 km
        val tier2 = all.filter { it.dist <= RADIUS_EXPANDED }.minByOrNull { it.dist }
        if (tier2 != null) {
            Log.d(TAG, "Tier2 (≤2km): ${tier2.place.name} @ ${"%.0f".format(tier2.dist)}m")
            return tier2.place to tier2.dist
        }

        // Tier 3: busy place ≤ 5 km
        val tier3 = all.filter { it.dist <= RADIUS_MAX && it.place.type in BUSY_TYPES }
            .minByOrNull { it.dist }
        if (tier3 != null) {
            Log.d(TAG, "Tier3 (busy ≤5km): ${tier3.place.name} @ ${"%.0f".format(tier3.dist)}m")
            return tier3.place to tier3.dist
        }

        // Tier 4: anything ≤ 5 km
        val tier4 = all.filter { it.dist <= RADIUS_MAX }.minByOrNull { it.dist }
        if (tier4 != null) {
            Log.d(TAG, "Tier4 (any ≤5km): ${tier4.place.name} @ ${"%.0f".format(tier4.dist)}m")
            return tier4.place to tier4.dist
        }

        Log.w(TAG, "No safe place within ${RADIUS_MAX}m")
        return null
    }

    /** Convenience wrapper. */
    fun findNearest(userLat: Double, userLng: Double): SafePlace? =
        findNearestWithDistance(userLat, userLng)?.first
}
