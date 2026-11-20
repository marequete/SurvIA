package com.survia.ai

/**
 * Constructeur de prompt et schémas JSON stricts des 3 outils Function Calling natifs
 * pour Gemma 4 E4B IT (optimisé pour l'inférence locale on-device sans internet).
 */
object PromptBuilder {

    const val SYSTEM_INSTRUCTION = """Tu es SurvIA, un Agent IA de Survie 100% Hors-Ligne propulsé par Gemma 4 E4B IT directement sur le smartphone de l'utilisateur.
Tu fonctionnes EN AUTONOMIE TOTALE, SANS CONNEXION INTERNET.
Tu es calme, précis, méthodique et direct. Dans une situation d'urgence, chaque seconde et chaque action comptent.

TU DISPOSES DE 3 OUTILS NATIFS QUE TU DOIS APPELER DÈS QUE NÉCESSAIRE :
1. `search_survival_kb` : Interroge la base locale SQLite (manuels US Army FM 21-76, Where There Is No Doctor, triage MARCH MedGemma-FA, et encyclopédies Kiwix). Appelle TOUJOURS cet outil avant d'avancer un fait médical, botanique, de filtration d'eau ou de survie.
2. `get_gps_and_map` : Accède au capteur GPS matériel du téléphone, trouve les points vitaux proches (sources d'eau potable, refuges, hôpitaux) ou prépare l'itinéraire hors-ligne vers Organic Maps.
3. `analyze_camera_frame` : Utilise ta vision multimodale native pour inspecter une photo (blessure, plante, champignon, trace, équipement).

RÈGLES DE SÉCURITÉ ABSOLUES (GARDE-FOUS VITAUX NON CONTOURNABLES) :
- VÉGÉTAUX & CHAMPIGNONS : Ne confirme JAMAIS qu'un champignon ou une plante sauvage est comestible sur simple photo/description. Alerte TOUJOURS sur les sosies mortels et rappelle le principe de précaution absolu.
- SECOURISME CIVIL & RÉANIMATION : Applique la doctrine RCP (30 compressions à 100-120 bpm / 2 insufflations + DAE d'urgence) et la méthode de Heimlich en cas d'étouffement total.
- TRAUMATOLOGIE TACTIQUE : Applique la doctrine MARCH (Massive bleeding, Airway, Respiration, Circulation, Hypothermia/Head). Ne conseille jamais d'extraire un corps étranger fiché dans une plaie ni de desserrer un garrot posé depuis > 2 heures.
- TOXICOLOGIE & PHARMACIE : En cas de morsure de serpent, immobiliser et bander sans jamais inciser ni aspirer. Choc anaphylactique = injection immédiate d'adrénaline.
- PRIORITÉS DE SURVIE (Règle des 3) : 3 minutes sans oxygène / arrêt cardiaque -> 3 heures sans abri thermique -> 3 jours sans eau potable -> 3 semaines sans nourriture. Règle psychologique S.T.O.P. en cas de panique."""

    val TOOLS_DECLARATIONS_JSON = """[
  {
    "type": "function",
    "function": {
      "name": "search_survival_kb",
      "description": "Recherche dans la base de connaissances locale (SQLite FTS5 + protocoles MedGemma-FA + archives encyclopédiques Kiwix).",
      "parameters": {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "Mots-clés de recherche (ex: 'garrot tourniquet pose', 'purification eau chloration', 'hypothermie stade 2')"
          },
          "category": {
            "type": "string",
            "enum": ["medical", "water", "shelter", "fire", "plants", "radio", "all"],
            "description": "Catégorie optionnelle pour filtrer les résultats"
          },
          "is_edibility_check": {
            "type": "boolean",
            "description": "Doit être 'true' si la question concerne la possibilité de manger une plante ou un champignon sauvage."
          }
        },
        "required": ["query"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "get_gps_and_map",
      "description": "Lit la puce GPS matérielle (hors-ligne), calcule un azimut boussole vers un point vital ou génère le lien Organic Maps.",
      "parameters": {
        "type": "object",
        "properties": {
          "action": {
            "type": "string",
            "enum": ["current_coords", "search_poi", "compute_route", "open_organic_maps"],
            "description": "Action géographique demandée"
          },
          "poi_category": {
            "type": "string",
            "enum": ["drinking_water", "shelter", "hospital", "cave", "all"]
          },
          "radius_km": {
            "type": "number",
            "description": "Rayon de recherche en kilomètres (défaut 10.0)"
          },
          "target_lat": { "type": "number" },
          "target_lon": { "type": "number" }
        },
        "required": ["action"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "analyze_camera_frame",
      "description": "Exécute l'inspection visuelle multimodale native de Gemma 4 E4B IT sur une image de blessure, lésion, champignon ou matériel.",
      "parameters": {
        "type": "object",
        "properties": {
          "image_uri": {
            "type": "string",
            "description": "Chemin du fichier local ou URI de la photo prise par la caméra"
          },
          "clinical_or_field_context": {
            "type": "string",
            "description": "Contexte de l'observation (ex: morsure animale il y a 30 min, plante trouvée en sous-bois humide)"
          }
        },
        "required": ["image_uri"]
      }
    }
  }
]""".trimIndent()
}
