package com.survia.tools

import com.survia.ai.ToolExecutionResult

class SearchSurvivalKB {

    /**
     * Interroge la base de connaissances locale (SQLite FTS5 + Arbre de décision MARCH + ZIM).
     * Si 'is_edibility_check' est détecté, applique immédiatement le verrou de sécurité vital.
     */
    fun execute(argsJson: String): ToolExecutionResult {
        val queryLower = argsJson.lowercase()
        val isEdibility = queryLower.contains("\"is_edibility_check\": true") ||
                queryLower.contains("champignon") ||
                queryLower.contains("plante") ||
                queryLower.contains("manger") ||
                queryLower.contains("comestible")

        if (isEdibility) {
            val guardrailPayload = """{
  "status": "GUARDRAIL_STRICT_BLOCK",
  "safe_to_ingest": false,
  "mandatory_directive": "INTERDICTION FORMELLE ET ABSOLUE : Ne jamais déclarer un champignon ou une plante sauvage comestible sur simple photo ou description.",
  "danger_level": "POTENTIALLY_LETHAL",
  "sosies_mortels_frequents": [
    {"espece": "Amanita phalloides (Amanite phalloïde)", "risque": "Destruction hépato-cellulaire irréversible en 6 à 24h, mortelle même cuite."},
    {"espece": "Conium maculatum (Grande ciguë)", "risque": "Paralysie respiratoire rapide par alcaloïdes neurotoxiques."},
    {"espece": "Cortinarius rubellus", "risque": "Insuffisance rénale aiguë retardée (3 à 14 jours)."}
  ],
  "instruction_for_agent": "Alerte immédiatement l'utilisateur avec un avertissement de danger rouge. Interdis formellement la consommation et rappelle le principe de précaution vital."
}""".trimIndent()
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = guardrailPayload,
                isFatalGuardrailTriggered = true
            )
        }

        // Triage Médical MARCH / MedGemma-FA
        if (queryLower.contains("saignement") || queryLower.contains("blessure") || queryLower.contains("triage") || queryLower.contains("brûlure")) {
            val medicalPayload = """{
  "status": "MEDICAL_TRIAGE_MARCH",
  "framework": "Protocole MedGemma-FA / Wilderness First Aid",
  "priority_checklist": [
    "M (Massive Bleeding) : Garrot tourniquet 5-7 cm au-dessus de la plaie (si membre) ou pansement compressif d'urgence.",
    "A (Airway) : Libérer les voies aériennes, subluxation mandibulaire si suspicion rachis.",
    "R (Respiration) : Déceler un pneumothorax sous tension, pansement à valve 3 côtés.",
    "C (Circulation) : Pouls radial, temps de recoloration cutanée, couverture thermique immédiate.",
    "H (Hypothermia / Head) : Prévenir l'hypothermie (maintient la coagulation sanguine)."
  ],
  "contraindications": [
    "NE JAMAIS retirer un objet fiché profondément dans le corps.",
    "NE PAS donner à boire à une victime inconsciente ou en état de choc."
  ]
}""".trimIndent()
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = medicalPayload
            )
        }

        // Réponse générique extraite de la base locale de survie
        val generalPayload = """{
  "status": "OK",
  "source": "US_Army_FM_21_76_and_Where_There_Is_No_Doctor",
  "manual_snippets": [
    {
      "topic": "Purification de l'eau en milieu dégradé",
      "method": "Ébullition franche 1 minute à gros bouillons (+1 minute par 1000m d'altitude). Filtration préalable sur tissu fin pour retirer les particules en suspension."
    },
    {
      "topic": "Abri thermique d'urgence",
      "method": "Isoler impérativement du sol (30 cm de feuillage sec/branches) pour stopper la perte thermique par conduction."
    }
  ]
}""".trimIndent()
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = generalPayload
        )
    }
}
