package com.survia.tools

import com.survia.ai.ToolExecutionResult
import kotlin.math.*

data class GeoCoords(val lat: Double, val lon: Double, val altitudeMeters: Double = 0.0)

/**
 * Outil GPS hors-ligne avec intégration du système de POI collaboratif utilisateur.
 *
 * Priorité de réponse :
 *  1. POIs ajoutés par l'utilisateur (UserPoiStore) — priorité maximale
 *  2. POIs statiques de référence (refuges, sources connues) — fallback
 *
 * Retourne du TEXTE LISIBLE (jamais du JSON brut) pour affichage direct dans le chat.
 */
class GetGpsAndMap(
    private val locationProvider: () -> GeoCoords,
    private val userPoiStore: UserPoiStore? = null
) {
    // POIs statiques de référence (hors-ligne, intégrés en dur)
    private val staticWaterPois = listOf(
        StaticPoi(45.1927, 5.7276, PoiType.WATER, "Source Font-Vive", "Eau de source, potable après filtration"),
        StaticPoi(45.1865, 5.7190, PoiType.WATER, "Ruisseau du Versoud", "Eau courante — bouillir avant consommation")
    )
    private val staticShelterPois = listOf(
        StaticPoi(45.1950, 5.7310, PoiType.SHELTER, "Refuge Belledonne Sud", "Refuge non gardé, 8 couchettes"),
        StaticPoi(45.1810, 5.7150, PoiType.SHELTER, "Cabane forestière Prémol", "Abri d'urgence forestier")
    )

    fun execute(argsJson: String): ToolExecutionResult {
        val current = locationProvider()
        val qLow = argsJson.lowercase()

        // Déterminer le type de POI recherché
        val targetType = when {
            containsAny(qLow, listOf("eau", "source", "boire", "potable", "riviere", "ruisseau")) -> PoiType.WATER
            containsAny(qLow, listOf("refuge", "abri", "bivouac", "nuit", "dormir", "shelter")) -> PoiType.SHELTER
            containsAny(qLow, listOf("danger", "eviter", "unsafe", "dangereux")) -> PoiType.DANGER
            containsAny(qLow, listOf("route", "chemin", "itineraire", "passer")) -> PoiType.SAFE_ROUTE
            else -> null // Tous les POIs
        }

        return buildPoiResponse(current, targetType)
    }

    private fun buildPoiResponse(current: GeoCoords, targetType: PoiType?): ToolExecutionResult {
        val sb = StringBuilder()
        var primaryUri: String? = null
        var primaryLabel: String? = null
        var hasUserPois = false

        // ── 1. POIs UTILISATEUR (priorité maximale) ───────────────────────────
        val userPois = if (userPoiStore != null) {
            if (targetType != null) {
                userPoiStore.getNearestPois(current.lat, current.lon, 10.0)
                    .filter { it.type == targetType }
            } else {
                userPoiStore.getNearestPois(current.lat, current.lon, 5.0)
            }
        } else emptyList()

        if (userPois.isNotEmpty()) {
            hasUserPois = true
            sb.appendLine("📍 VOS POINTS SAUVEGARDÉS (en mémoire hors-ligne) :")
            userPois.take(3).forEachIndexed { idx, poi ->
                val dist = haversineKm(current.lat, current.lon, poi.lat, poi.lon)
                val bearing = initialBearing(current.lat, current.lon, poi.lat, poi.lon)
                val cardinal = bearingToCardinal(bearing)
                val distStr = if (dist < 1.0) "${"%.0f".format(dist * 1000)} m" else "${"%.1f".format(dist)} km"

                sb.append("  ${poi.type.emoji} ${poi.label} — $distStr $cardinal")
                if (poi.comment != null) sb.append("\n     ↳ Note : ${poi.comment}")
                sb.appendLine()

                if (idx == 0) {
                    // Bouton GPS pour le POI le plus proche
                    primaryUri = "om://map?ll=${poi.lat},${poi.lon}&n=${poi.label.replace(" ", "_")}"
                    primaryLabel = "📍 Ouvrir ${poi.label} ($distStr $cardinal)"
                }
            }
            sb.appendLine()
        }

        // ── 2. POIs STATIQUES DE RÉFÉRENCE (fallback ou complément) ──────────
        val staticPois = when (targetType) {
            PoiType.WATER -> staticWaterPois
            PoiType.SHELTER -> staticShelterPois
            null -> staticWaterPois + staticShelterPois
            else -> emptyList()
        }

        val nearestStatic = staticPois
            .map { p -> Pair(p, haversineKm(current.lat, current.lon, p.lat, p.lon)) }
            .sortedBy { it.second }

        if (nearestStatic.isNotEmpty()) {
            if (hasUserPois) sb.appendLine("📌 POINTS DE RÉFÉRENCE HORS-LIGNE :")
            else sb.appendLine("📌 POINTS D'EAU / REFUGES HORS-LIGNE :")

            nearestStatic.take(2).forEach { (poi, dist) ->
                val bearing = initialBearing(current.lat, current.lon, poi.lat, poi.lon)
                val cardinal = bearingToCardinal(bearing)
                val distStr = if (dist < 1.0) "${"%.0f".format(dist * 1000)} m" else "${"%.1f".format(dist)} km"
                val walkMin = max(1, ((dist / 4.0) * 60).toInt())

                sb.append("  ${poi.type.emoji} ${poi.label} — $distStr $cardinal (~$walkMin min)")
                if (poi.comment != null) sb.append("\n     ↳ ${poi.comment}")
                sb.appendLine()

                // Premier POI statique → bouton GPS si aucun POI utilisateur
                if (primaryUri == null) {
                    primaryUri = "om://map?ll=${poi.lat},${poi.lon}&n=${poi.label.replace(" ", "_")}"
                    primaryLabel = "📍 Naviguer vers ${poi.label} ($distStr $cardinal)"
                }
            }
            sb.appendLine()
        }

        // ── 3. POSITION ACTUELLE ──────────────────────────────────────────────
        sb.appendLine("📡 Position actuelle : ${"%,.4f".format(current.lat)}°N, ${"%,.4f".format(current.lon)}°E (alt. ${current.altitudeMeters.toInt()} m)")
        sb.append("💡 Conseil : Utilisez le bouton ci-dessous pour ouvrir Organic Maps 100% hors-ligne.")

        return ToolExecutionResult(
            toolName = "get_gps_and_map",
            outputJson = sb.toString().trimEnd(),
            actionButtonText = primaryLabel,
            actionButtonUri = primaryUri
        )
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = (lat2 - lat1) * (PI / 180.0)
        val dLon = (lon2 - lon1) * (PI / 180.0)
        val a = sin(dLat / 2).pow(2.0) + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2.0)
        return 2.0 * r * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
    }

    private fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * PI / 180.0
        val phi2 = lat2 * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (atan2(y, x) * (180.0 / PI) + 360.0) % 360.0
    }

    private fun bearingToCardinal(deg: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return dirs[((deg + 22.5) / 45.0).toInt() % 8]
    }
}

/** POI statique intégré en dur (référence hors-ligne, non éditable par l'utilisateur) */
private data class StaticPoi(
    val lat: Double,
    val lon: Double,
    val type: PoiType,
    val label: String,
    val comment: String? = null
)
