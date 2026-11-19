"""
Domain 2: Survival Manuals RAG & Botanical/Fungal Safety Guardrails
Unifies `JoshKappler/survival-station` core features:
- Full-text search across offline survival manuals (US Army FM 21-76, Where There Is No Doctor, Solar/Radio)
- Strict non-bypassable botanical & mycological safety guardrails (forbids AI from declaring wild plants/mushrooms safe)
- Instant step-by-step emergency survival protocols
"""

from typing import Any

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase


EMERGENCY_PROTOCOLS: dict[str, dict[str, Any]] = {
    "hypothermia": {
        "title": "Urgence Hypothermie & Froid Extrême",
        "priority": "CRITICAL_THERMAL",
        "immediate_steps": [
            "1. Isoler immédiatement le sujet du sol (minimum 30 cm de végétation sèche ou matelas) et du vent.",
            "2. Retirer les vêtements mouillés uniquement si un abri sec et des vêtements/couvertures secs sont prêts.",
            "3. Réchauffer le tronc en priorité (bouillotte tiède sous les aisselles et l'aine, contact peau-à-peau dans un sac de couchage).",
            "4. Ne JAMAIS frictionner les membres gelés ni donner d'alcool ; administrer des boissons chaudes sucrées seulement si la personne est pleinement consciente.",
        ],
    },
    "hemorrhage": {
        "title": "Contrôle d'Hémorragie Externe Massive (Protocole MARCH)",
        "priority": "CRITICAL_LIFE_THREAT",
        "immediate_steps": [
            "1. Compression manuelle directe et continue sur la plaie avec un linge propre ou pansement compressif pendant 10 minutes.",
            "2. Si saignement artériel pulsatile d'un membre non contrôlé : poser un garrot 5-7 cm au-dessus de la plaie (jamais sur le genou/coude).",
            "3. Noter l'heure exacte de pose du garrot (HH:MM) sur le front du blessé.",
            "4. Allonger le blessé, couvrir contre l'hypothermie (triade létale) et préparer l'évacuation.",
        ],
    },
    "water_purification": {
        "title": "Protocole de Sécurisation d'Eau Potable en Terrain Hostile",
        "priority": "HIGH_HYDRATION",
        "immediate_steps": [
            "1. Privilégier l'eau de source jaillissant de la roche en amont des pâturages.",
            "2. Pré-filtrer avec un tissu serré puis charbon de bois + sable fin.",
            "3. Faire bouillir à gros bouillons pendant 1 minute (3 minutes au-dessus de 2000 m).",
            "4. Alternative chimique : 2 à 4 gouttes de Javel 5% non parfumée par litre, attendre 30 minutes.",
        ],
    },
    "radio_distress": {
        "title": "Procédure d'Appel de Détresse Radio MAYDAY (HF / VHF / PMR446)",
        "priority": "HIGH_RESCUE",
        "immediate_steps": [
            "1. Sélectionner la fréquence d'urgence (VHF Marine 156.800 MHz Canal 16 / PMR446 Canal 8-16 ou 7-7 Montagne).",
            "2. Émettre en hauteur et à découvert : 'MAYDAY MAYDAY MAYDAY, ici [Nom], position GPS [Lat, Lon], nature de l'urgence, nombre de blessés, j'écoute'.",
            "3. Émettre pendant les 5 premières minutes de chaque heure (Silence radio H+00 à H+03) pour économiser la batterie.",
        ],
    },
}


def survival_search_manuals(
    query: str,
    category: str | None = None,
    limit: int = 5,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """Searches the offline survival manuals database using BM25 / FTS5 ranking."""
    cfg = config or SurvivalConfig()
    db = SurvivalDatabase(cfg)
    results = db.search_manuals(query=query, category=category, limit=limit)
    return {
        "query": query,
        "category_filter": category or "all",
        "count": len(results),
        "results": results,
    }


def survival_verify_edibility(
    specimen_name: str,
    characteristics: str | None = None,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Enforces `survival-station`'s strict botanical & mycological safety guardrail.
    Never certifies a wild plant or mushroom as safe to ingest; returns lethal lookalikes
    and mandatory cross-verification steps.
    """
    cfg = config or SurvivalConfig()
    db = SurvivalDatabase(cfg)
    combined_query = f"{specimen_name} {characteristics or ''}".strip()
    matched = db.query_botanical_guardrails(combined_query)

    return {
        "specimen_queried": specimen_name,
        "characteristics_provided": characteristics or "",
        "safe_to_ingest": False,
        "guardrail_status": "STRICT_BLOCK_NEVER_CONSUME_UNVERIFIED",
        "mandatory_safety_directive": (
            "DIRECTIVE VITALE SURVIVAL-STATION : Il est formellement INTERDIT à l'agent IA de déclarer "
            "une plante sauvage, une baie ou un champignon comestible à partir d'une description ou d'une photo. "
            "L'apport calorique d'un champignon ou d'une feuille est négligeable face au risque mortel "
            "(amatoxines hépatotoxiques, coniine neurotoxique, taxine cardiotoxique). "
            "NE JAMAIS INGÉRER sans identification physique certaine à 100% dans deux manuels botaniques de référence."
        ),
        "matched_hazards": matched,
        "universal_edibility_test_reminder": [
            "Ne JAMAIS appliquer le test universel de comestibilité aux champignons (les toxines fongiques attaquent le foie après 12-24h sans brûler la bouche).",
            "Éviter systématiquement : ombelles blanches (type carotte/ciguë), sève laiteuse colorée, baies blanches ou jaunes, odeur d'amande amère (cyanure), plantes à bulbe ressemblant à l'ail sans odeur d'ail.",
        ],
    }


def survival_emergency_protocol(
    scenario: str,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """Returns a structured, verified step-by-step survival protocol for critical situations."""
    cfg = config or SurvivalConfig()
    key = scenario.strip().lower()
    for proto_key, proto_data in EMERGENCY_PROTOCOLS.items():
        if proto_key in key or key in proto_key:
            return {
                "scenario": proto_key,
                **proto_data,
            }

    # Fallback search in manuals
    db = SurvivalDatabase(cfg)
    manuals = db.search_manuals(scenario, limit=2)
    return {
        "scenario": scenario,
        "title": f"Protocole d'urgence : {scenario}",
        "priority": "GENERAL_SURVIVAL",
        "immediate_steps": [
            "1. Appliquer la règle STOP : S'arrêter (Stop), Réfléchir (Think), Observer (Observe), Planifier (Plan).",
            "2. Sécuriser dans l'ordre de la Règle des 3 : Air/Hémorragie (3 min) -> Abri thermique (3h) -> Eau potable (3 jours) -> Nourriture (3 semaines).",
            *(m["content"] for m in manuals),
        ],
    }

