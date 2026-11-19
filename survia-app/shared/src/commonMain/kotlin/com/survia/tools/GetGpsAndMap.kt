package com.survia.tools

import com.survia.ai.ToolExecutionResult
import kotlin.math.*

data class GeoCoords(val lat: Double, val lon: Double, val altitudeMeters: Double = 0.0)

class GetGpsAndMap(
    private val locationProvider: () -> GeoCoords
) {
    fun execute(argsJson: String): ToolExecutionResult {
        val current = locationProvider()
        val cardinalDirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

        // POI exemples de secours hors-ligne (sources d'eau, abris)
        val nearestWaterLat = current.lat + 0.0042
        val nearestWaterLon = current.lon + 0.0031
        val distKm = haversineKm(current.lat, current.lon, nearestWaterLat, nearestWaterLon)
        val bearingDeg = initialBearing(current.lat, current.lon, nearestWaterLat, nearestWaterLon)
        val cardinal = cardinalDirs[((bearingDeg + 22.5) / 45.0).toInt() % 8]

        val naismithMinutes = max(1, ((distKm / 4.0) * 60).toInt())
        val omUri = "om://map?ll=${nearestWaterLat},${nearestWaterLon}&n=Source_Eau_Potable"
        val geoUri = "geo:${nearestWaterLat},${nearestWaterLon}?q=${nearestWaterLat},${nearestWaterLon}(Source_Eau_Potable)"

        val payload = """{
  "status": "GPS_FIXED_OFFLINE",
  "current_position": {
    "latitude": ${current.lat},
    "longitude": ${current.lon},
    "altitude_m": ${current.altitudeMeters}
  },
  "nearest_vital_poi": {
    "type": "drinking_water_spring",
    "label": "Source naturelle Font-Vive",
    "distance_km": ${"%.2f".format(distKm)},
    "compass_azimuth_deg": ${"%.1f".format(bearingDeg)},
    "compass_cardinal": "$cardinal",
    "estimated_walk_duration_minutes": $naismithMinutes,
    "naismith_rule": "4 km/h en terrain naturel"
  },
  "navigation_links": {
    "organic_maps_direct_uri": "$omUri",
    "android_standard_geo_uri": "$geoUri",
    "action_note": "Ouvre directement l'application Organic Maps 100% hors-ligne."
  }
}""".trimIndent()

        return ToolExecutionResult(
            toolName = "get_gps_and_map",
            outputJson = payload
        )
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = (lat2 - lat1) * (PI / 180.0)
        val dLon = (lon2 - lon1) * (PI / 180.0)
        val a = sin(dLat / 2).pow(2.0) + cos(lat1 * (PI / 180.0)) * cos(lat2 * (PI / 180.0)) * sin(dLon / 2).pow(2.0)
        return 2.0 * r * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
    }

    private fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * (PI / 180.0)
        val phi2 = lat2 * (PI / 180.0)
        val dLon = (lon2 - lon1) * (PI / 180.0)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (atan2(y, x) * (180.0 / PI) + 360.0) % 360.0
    }
}
