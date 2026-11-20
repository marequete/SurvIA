package com.survia.tools

import kotlin.math.*

/**
 * Base de données collaborative hors-ligne des Points d'Intérêt (POI) utilisateur.
 *
 * Persistence JSON sur le système de fichiers local (Android : filesDir).
 * Aucune dépendance externe — utilise uniquement kotlin.text et kotlin.math.
 *
 * L'utilisateur peut ajouter, annoter et supprimer des POIs :
 *   - 💧 Sources d'eau, points d'eau
 *   - 🏕️ Refuges et abris
 *   - ⚠️ Zones dangereuses (rues, secteurs à éviter)
 *   - 🛣️ Itinéraires sûrs balisés
 *   - 📌 Commentaires et remarques libres
 *
 * L'IA peut consulter ces POIs dynamiquement via getNearestPois().
 */

enum class PoiType(val emoji: String, val label: String) {
    WATER("💧", "Source d'eau"),
    SHELTER("🏕️", "Refuge / Abri"),
    DANGER("⚠️", "Zone dangereuse"),
    SAFE_ROUTE("🛣️", "Itinéraire sûr"),
    GENERAL("📌", "Point d'intérêt")
}

data class UserPoi(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: PoiType,
    val label: String,
    val comment: String? = null,
    val addedAt: Long,
    val isUserAdded: Boolean = true
)

class UserPoiStore(private val dataDir: String) {

    private val fileName = "user_pois.json"
    private val pois: MutableList<UserPoi> = mutableListOf()
    private var loaded = false

    // ─── Chargement paresseux ──────────────────────────────────────────────────

    private fun ensureLoaded() {
        if (!loaded) {
            loadFromDisk()
            loaded = true
        }
    }

    // ─── API Publique ──────────────────────────────────────────────────────────

    fun addPoi(poi: UserPoi) {
        ensureLoaded()
        pois.removeAll { it.id == poi.id } // idempotent: remplace si même ID
        pois.add(poi)
        saveToDisk()
    }

    fun getAllPois(): List<UserPoi> {
        ensureLoaded()
        return pois.toList()
    }

    fun getPoisByType(type: PoiType): List<UserPoi> {
        ensureLoaded()
        return pois.filter { it.type == type }
    }

    /**
     * Retourne les POIs dans un rayon donné (défaut 5 km), triés par distance croissante.
     */
    fun getNearestPois(lat: Double, lon: Double, radiusKm: Double = 5.0): List<UserPoi> {
        ensureLoaded()
        return pois
            .map { poi -> Pair(poi, haversineKm(lat, lon, poi.lat, poi.lon)) }
            .filter { (_, dist) -> dist <= radiusKm }
            .sortedBy { (_, dist) -> dist }
            .map { (poi, _) -> poi }
    }

    /**
     * Retourne le POI le plus proche d'un type donné dans un rayon donné.
     */
    fun getNearestPoiOfType(lat: Double, lon: Double, type: PoiType, radiusKm: Double = 10.0): UserPoi? {
        ensureLoaded()
        return pois
            .filter { it.type == type }
            .map { poi -> Pair(poi, haversineKm(lat, lon, poi.lat, poi.lon)) }
            .filter { (_, dist) -> dist <= radiusKm }
            .minByOrNull { (_, dist) -> dist }
            ?.first
    }

    fun removePoi(id: String) {
        ensureLoaded()
        pois.removeAll { it.id == id }
        saveToDisk()
    }

    fun count(): Int {
        ensureLoaded()
        return pois.size
    }

    // ─── Formatage texte pour l'IA ─────────────────────────────────────────────

    /**
     * Génère un résumé lisible des POIs proches pour insertion dans la réponse de l'IA.
     */
    fun formatNearestForAI(lat: Double, lon: Double, radiusKm: Double = 5.0): String? {
        val nearest = getNearestPois(lat, lon, radiusKm)
        if (nearest.isEmpty()) return null

        return buildString {
            appendLine("📍 VOS POINTS D'INTÉRÊT SAUVEGARDÉS (rayon ${radiusKm.toInt()} km) :")
            nearest.take(5).forEach { poi ->
                val dist = haversineKm(lat, lon, poi.lat, poi.lon)
                val bearing = initialBearing(lat, lon, poi.lat, poi.lon)
                val cardinal = bearingToCardinal(bearing)
                append("  ${poi.type.emoji} ${poi.label} — ${"%.0f".format(dist * 1000)} m $cardinal")
                if (poi.comment != null) append(" (${poi.comment})")
                appendLine()
            }
        }.trimEnd()
    }

    /**
     * Génère l'URI Organic Maps pour le POI le plus proche d'un type donné.
     */
    fun getOrganicMapsUri(lat: Double, lon: Double, type: PoiType): String? {
        val poi = getNearestPoiOfType(lat, lon, type) ?: return null
        val encodedLabel = poi.label.replace(" ", "_")
        return "om://map?ll=${poi.lat},${poi.lon}&n=$encodedLabel"
    }

    // ─── Persistence JSON (implémentation manuelle, sans dépendances) ──────────

    private fun saveToDisk() {
        try {
            val file = java.io.File(dataDir, fileName)
            file.parentFile?.mkdirs()
            val json = buildString {
                appendLine("[")
                pois.forEachIndexed { index, poi ->
                    append("  {")
                    append("\"id\":\"${poi.id}\",")
                    append("\"lat\":${poi.lat},")
                    append("\"lon\":${poi.lon},")
                    append("\"type\":\"${poi.type.name}\",")
                    append("\"label\":\"${poi.label.replace("\"", "\\\"")}\",")
                    append("\"comment\":${if (poi.comment != null) "\"${poi.comment.replace("\"", "\\\"")}\"" else "null"},")
                    append("\"addedAt\":${poi.addedAt},")
                    append("\"isUserAdded\":${poi.isUserAdded}")
                    append("}")
                    if (index < pois.size - 1) appendLine(",") else appendLine()
                }
                append("]")
            }
            file.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {
            // Persistence silencieuse — l'app reste fonctionnelle même sans écriture disque
        }
    }

    private fun loadFromDisk() {
        try {
            val file = java.io.File(dataDir, fileName)
            if (!file.exists()) return
            val text = file.readText(Charsets.UTF_8).trim()
            if (text.isEmpty() || text == "[]") return

            // Parser JSON minimal sans dépendances externes
            parseJsonArray(text).forEach { obj ->
                try {
                    val id = extractJsonString(obj, "id") ?: return@forEach
                    val lat = extractJsonDouble(obj, "lat") ?: return@forEach
                    val lon = extractJsonDouble(obj, "lon") ?: return@forEach
                    val typeName = extractJsonString(obj, "type") ?: "GENERAL"
                    val label = extractJsonString(obj, "label") ?: "Point d'intérêt"
                    val comment = extractJsonString(obj, "comment")
                    val addedAt = extractJsonLong(obj, "addedAt") ?: System.currentTimeMillis()
                    val isUserAdded = extractJsonBoolean(obj, "isUserAdded") ?: true
                    val type = try { PoiType.valueOf(typeName) } catch (_: Exception) { PoiType.GENERAL }
                    pois.add(UserPoi(id, lat, lon, type, label, comment, addedAt, isUserAdded))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // ─── Parser JSON minimaliste ───────────────────────────────────────────────

    private fun parseJsonArray(json: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            when (json[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        result.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun extractJsonString(obj: String, key: String): String? {
        val pattern = "\"$key\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val match = pattern.find(obj) ?: return null
        return match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractJsonDouble(obj: String, key: String): Double? {
        val pattern = "\"$key\":\\s*(-?[0-9]+\\.?[0-9]*)".toRegex()
        return pattern.find(obj)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractJsonLong(obj: String, key: String): Long? {
        val pattern = "\"$key\":\\s*(-?[0-9]+)".toRegex()
        return pattern.find(obj)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractJsonBoolean(obj: String, key: String): Boolean? {
        val pattern = "\"$key\":\\s*(true|false)".toRegex()
        return pattern.find(obj)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    // ─── Géodésie ─────────────────────────────────────────────────────────────

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
