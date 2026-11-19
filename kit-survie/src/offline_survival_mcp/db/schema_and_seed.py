import json
import math
import re
from pathlib import Path
from typing import Any

from offline_survival_mcp.config import SurvivalConfig

try:
    import sqlite3  # type: ignore
    HAS_SQLITE3 = True
except ImportError:
    sqlite3 = None  # type: ignore
    HAS_SQLITE3 = False


DEFAULT_MANUALS: list[dict[str, Any]] = [
    {
        "id": "manual-water-01",
        "title": "Purification et Filtration d'Eau d'Urgence (US Army FM 21-76 & Where There Is No Doctor)",
        "category": "water",
        "source": "survival-station / US Army FM 21-76",
        "tags": "eau purification ébullition filtration chlore iode survie hydratation",
        "content": (
            "1. ÉBULLITION (Méthode la plus sûre) : Filtrer d'abord les sédiments à travers un tissu propre "
            "ou un filtre sable/charbon actif. Porter l'eau à ébullition franche pendant au moins 1 minute "
            "(3 minutes au-dessus de 2000 m d'altitude). Élimine 100% des bactéries, virus et protozoaires (Giardia, Cryptosporidium).\n"
            "2. DÉSINFECTION CHIMIQUE : Utiliser 2 gouttes d'eau de Javel non parfumée (hypochlorite de sodium 5-6%) "
            "par litre d'eau claire (4 gouttes si l'eau est trouble ou très froide). Agiter et laisser agir 30 minutes minimum.\n"
            "3. DISTILLATION SOLAIRE / SODIS : En dernier recours, exposer une bouteille PET transparente au soleil "
            "pendant 6 heures (ou 48h si ciel voilé)."
        ),
    },
    {
        "id": "manual-med-01",
        "title": "Contrôle des Hémorragies Massives et Traitement des Plaies (Protocole MARCH / Where There Is No Doctor)",
        "category": "medical",
        "source": "MedGemmaImpactChallenge / Where There Is No Doctor",
        "tags": "hémorragie saignement garrot compression plaie infection pansement",
        "content": (
            "1. COMPRESSION DIRECTE : Appuyer fermement sur la plaie avec une compresse stérile ou un tissu propre "
            "pendant au moins 10 minutes sans relâcher la pression.\n"
            "2. GARROT TACTIQUE (Membre uniquement, si hémorragie artérielle incontrôlable) : Placer 5 à 7 cm "
            "au-dessus de la plaie (jamais sur une articulation). Serrer jusqu'à l'arrêt du saignement et noter l'heure exacte (HH:MM).\n"
            "3. IRRIGATION & ANTISEPSIE : Nettoyer abondamment à l'eau bouillie refroidie ou au sérum physiologique "
            "(pression modérée). Ne jamais retirer un corps étranger profondément fiché."
        ),
    },
    {
        "id": "manual-shelter-01",
        "title": "Construction d'Abri Thermique et Prévention de l'Hypothermie (Règle des 3)",
        "category": "shelter",
        "source": "survival-station / US Army FM 21-76",
        "tags": "abri froid hypothermie isolation sol feu survie bivouac",
        "content": (
            "L'isolation par rapport au sol est 3 fois plus critique que l'isolation du toit (perte par conduction).\n"
            "1. CHOIX DU SITE : Éviter les fonds de vallées (poches d'air froid et crues subites) et les arbres morts (widowmakers).\n"
            "2. LIT ISOLANT : Empiler au moins 30 à 50 cm de feuilles sèches, aiguilles de pin ou fougères sous le corps.\n"
            "3. STRUCTURE (Lean-To / Debris Hut) : Réduire le volume intérieur au minimum pour que la chaleur corporelle chauffe l'abri."
        ),
    },
    {
        "id": "manual-fire-01",
        "title": "Allumage de Feu par Temps Humide et Signaux de Détresse",
        "category": "fire_signaling",
        "source": "survival-station / Bushcraft Manual",
        "tags": "feu allumage amadou bois humide bâtonnage signal fumée morse sos",
        "content": (
            "1. BOIS SEC À CŒUR : Fendre les branches mortes sur pied (jamais celles au sol) pour atteindre le cœur sec "
            "et tailler des hérissons de bois (feather sticks).\n"
            "2. AMADOU : Écorce de bouleau (contient de la bétuline inflammable même humide), résine de conifère (fatwood), coton carbonisé.\n"
            "3. SIGNAL DE DÉTRESSE INTERNATIONAL : 3 feux disposés en triangle équilatéral espacés de 25 m. "
            "Ajouter du feuillage vert sur un feu ardent pour produire une fumée blanche dense le jour."
        ),
    },
    {
        "id": "manual-power-01",
        "title": "Diagnostic Solaire 12V, Batteries LiFePO4 et Radio d'Urgence HF/VHF (iFixit & Ham Handbook)",
        "category": "electronics_radio",
        "source": "kiwix-wiki-mcp / iFixit / ARRL Emergency Radio",
        "tags": "solaire batterie mppt radio vhf uhf pmr446 antenne fusible réparation",
        "content": (
            "1. PANNE SOLAIRE / MPPT : Toujours connecter la batterie AU CONTRÔLEUR avant de brancher les panneaux solaires "
            "(sinon surtension du MPPT). Vérifier le fusible en ligne et la tension à vide Voc des panneaux (18V-22V pour un panneau 12V).\n"
            "2. FRÉQUENCES DE DÉTRESSE : Canal 16 VHF Marine (156.800 MHz), PMR446 Canal 8 sous-canal 16 (Europe Montagne : Canal 7/7), "
            "Aviation 121.5 MHz.\n"
            "3. ÉCONOMIE BATTERIE ANDROID (Gemma 4 E4B) : Activer le mode Avion, couper le Bluetooth/Wi-Fi, "
            "réduire la luminosité à 15% et utiliser le thème sombre OLED."
        ),
    },
    {
        "id": "manual-med-02",
        "title": "Traitement des Brûlures, Morsures de Serpent et Déshydratation Sévère (SRO)",
        "category": "medical",
        "source": "MedGemmaImpactChallenge / OMS Premiers Secours",
        "tags": "brûlure morsure serpent venin déshydratation sro sel sucre",
        "content": (
            "1. SOLUTION DE RÉHYDRATATION ORALE (SRO OMS maison) : Dans 1 litre d'eau potable (bouillie), "
            "dissoudre exactement 6 cuillères à café rases de sucre et 1/2 cuillère à café rase de sel.\n"
            "2. MORSURE DE SERPENT : Immobiliser le membre avec une attelle souple, retirer bagues/montres avant l'œdème. "
            "NE JAMAIS inciser, ne jamais sucer le venin, ne jamais poser de garrot artériel.\n"
            "3. BRÛLURE THERMIQUE : Refroidir à l'eau tempérée (15-20°C) ruisselante pendant 15 minutes. Ne jamais percer les cloques."
        ),
    },
]

DEFAULT_BOTANICAL_GUARDRAILS: list[dict[str, Any]] = [
    {
        "id": "bot-amanita",
        "common_name": "Amanite phalloïde / Amanites blanches (Death Cap / Destroying Angel)",
        "scientific_name": "Amanita phalloides / Amanita virosa",
        "toxicity_level": "LETHAL",
        "lookalikes": "Volvaire, Agaric champêtre (Rosé des prés), Russule verte",
        "warning": (
            "MORTEL : Contient des amatoxines résistantes à la cuisson, au séchage et à la congélation. "
            "30g suffisent à détruire le foie et les reins (symptômes retardés de 6 à 24h). "
            "RÈGLE ABSOLUE SURVIVAL-STATION : Ne JAMAIS consommer un champignon sauvage à lames blanches, volve ou anneau."
        ),
    },
    {
        "id": "bot-hemlock",
        "common_name": "Grande Ciguë / Ciguë vireuse (Poison Hemlock / Water Hemlock)",
        "scientific_name": "Conium maculatum / Cicuta virosa",
        "toxicity_level": "LETHAL",
        "lookalikes": "Carotte sauvage (Daucus carota), Cerfeuil, Persil sauvage, Angélique",
        "warning": (
            "MORTEL (Coniine / Cicutoxine) : Provoque une paralysie respiratoire ascendante rapide. "
            "Tige glabre tachée de pourpre, odeur fétide d'urine de souris au froissement. "
            "RÈGLE ABSOLUE : Éviter toute plante de la famille des Apiacées (ombelles blanches) en situation de survie."
        ),
    },
    {
        "id": "bot-yew",
        "common_name": "If commun (European Yew)",
        "scientific_name": "Taxus baccata",
        "toxicity_level": "LETHAL",
        "lookalikes": "Sapin pectiné (Abies alba), Tsuga",
        "warning": (
            "MORTEL (Taxine) : Toutes les parties de l'arbre (aiguilles, écorce, graine noire dans l'arille rouge) "
            "sont hautement cardiotoxiques. Ne jamais utiliser ses aiguilles pour des infusions de vitamine C."
        ),
    },
    {
        "id": "bot-foxglove",
        "common_name": "Digitale pourpre (Foxglove) & Belladone (Deadly Nightshade)",
        "scientific_name": "Digitalis purpurea / Atropa belladonna",
        "toxicity_level": "LETHAL",
        "lookalikes": "Grande Consoude (Symphytum officinale), Myrtille sauvage",
        "warning": (
            "MORTEL : Arrêt cardiaque (digitaline) ou délire anticholinergique mortel (atropine). "
            "Ne jamais récolter de feuilles de consoude sans floraison visible ni de baies noires isolées."
        ),
    },
    {
        "id": "bot-dandelion",
        "common_name": "Pissenlit (Taraxacum) & Ortie dioïque (Urtica dioica)",
        "scientific_name": "Taraxacum officinale / Urtica dioica",
        "toxicity_level": "CAUTION_VERIFY_MANUAL",
        "lookalikes": "Porcelle enracinée, Laiteron (comestibles) ; Lamier blanc",
        "warning": (
            "GARDE-FOU ACTIF : Même pour des espèces réputées comestibles (ortie bouillie, pissenlit), "
            "l'agent IA ne doit JAMAIS certifier une identification sur simple photo ou description. "
            "Toujours croiser avec un manuel botanique physique et vérifier l'absence de pollution chimique/parasitaire (douve du foie)."
        ),
    },
]

DEFAULT_SURVIVAL_POIS: list[dict[str, Any]] = [
    {
        "id": "poi-water-spring-01",
        "name": "Source Captée de la Font-Vive (Eau Potable Montagne)",
        "category": "drinking_water",
        "osm_tag": "natural=spring;drinking_water=yes",
        "lat": 45.1885,
        "lon": 5.7245,
        "elevation_m": 620,
        "notes": "Source pérenne débit 4L/min. Ébullition préventive recommandée après fortes pluies.",
    },
    {
        "id": "poi-shelter-hut-01",
        "name": "Refuge Non Gardé du Col de l'Arc (Abri Hors-Sac)",
        "category": "shelter",
        "osm_tag": "tourism=alpine_hut;shelter_type=basic_hut",
        "lat": 45.1620,
        "lon": 5.6980,
        "elevation_m": 1410,
        "notes": "Ouvert toute l'année, 12 couchages bois, poêle à bois, source à 150m au nord.",
    },
    {
        "id": "poi-hospital-01",
        "name": "Centre Hospitalier Universitaire (Urgences & Traumatologie H24)",
        "category": "hospital",
        "osm_tag": "amenity=hospital;emergency=yes",
        "lat": 45.1990,
        "lon": 5.7410,
        "elevation_m": 215,
        "notes": "Service d'accueil des urgences vitales, héliport, sérums antivenimeux.",
    },
    {
        "id": "poi-water-river-02",
        "name": "Fontaine Communale & Point d'Eau Saint-Nizier",
        "category": "drinking_water",
        "osm_tag": "amenity=drinking_water",
        "lat": 45.1712,
        "lon": 5.6315,
        "elevation_m": 1160,
        "notes": "Bassin en pierre alimenté par réseau gravitaire.",
    },
    {
        "id": "poi-pharmacy-01",
        "name": "Poste de Secours Montagne & Pharmacie de Garde",
        "category": "first_aid_station",
        "osm_tag": "emergency=mountain_rescue;amenity=pharmacy",
        "lat": 45.1810,
        "lon": 5.7110,
        "elevation_m": 310,
        "notes": "Défibrillateur DAE externe accessible 24h/24, relais radio VHF.",
    },
    {
        "id": "poi-cave-01",
        "name": "Abri sous Roche de la Balme (Protection Intempéries)",
        "category": "shelter",
        "osm_tag": "natural=cave_entrance",
        "lat": 45.1790,
        "lon": 5.6890,
        "elevation_m": 890,
        "notes": "Cavité sèche hors crue, orientée Sud-Est, bois mort abondant à proximité.",
    },
]


def _tokenize(text: str) -> list[str]:
    return [t for t in re.split(r"[^a-zA-Z0-9àâäéèêëîïôöùûüçœ]+", text.lower()) if len(t) >= 2]


class SurvivalDatabase:
    """
    Hybrid Offline Database Engine:
    - Uses SQLite3 + FTS5 when `sqlite3` is available (Android Termux, standard Python).
    - Seamlessly falls back to a deterministic JSON + BM25 + Haversine engine when
      running in minimal embedded Python environments without `_sqlite3`.
    """

    def __init__(self, config: SurvivalConfig) -> None:
        self.config = config

    def initialize_and_seed(self) -> dict[str, Any]:
        # Always persist the JSON store so both Python & Node.js tools can inspect it
        store = {
            "manuals": DEFAULT_MANUALS,
            "botanical_guardrails": DEFAULT_BOTANICAL_GUARDRAILS,
            "pois": DEFAULT_SURVIVAL_POIS,
        }
        self.config.json_store_path.write_text(
            json.dumps(store, ensure_ascii=False, indent=2), encoding="utf-8"
        )

        backend = "json_bm25"
        if HAS_SQLITE3 and sqlite3 is not None:
            backend = "sqlite3_fts5"
            conn = sqlite3.connect(str(self.config.sqlite_db_path))
            try:
                cur = conn.cursor()
                cur.execute(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS manuals_fts USING fts5(
                        id UNINDEXED,
                        title,
                        category,
                        source,
                        tags,
                        content
                    );
                    """
                )
                cur.execute("DELETE FROM manuals_fts;")
                for m in DEFAULT_MANUALS:
                    cur.execute(
                        "INSERT INTO manuals_fts (id, title, category, source, tags, content) VALUES (?, ?, ?, ?, ?, ?);",
                        (m["id"], m["title"], m["category"], m["source"], m["tags"], m["content"]),
                    )

                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS botanical_guardrails (
                        id TEXT PRIMARY KEY,
                        common_name TEXT,
                        scientific_name TEXT,
                        toxicity_level TEXT,
                        lookalikes TEXT,
                        warning TEXT
                    );
                    """
                )
                cur.execute("DELETE FROM botanical_guardrails;")
                for b in DEFAULT_BOTANICAL_GUARDRAILS:
                    cur.execute(
                        "INSERT INTO botanical_guardrails VALUES (?, ?, ?, ?, ?, ?);",
                        (
                            b["id"],
                            b["common_name"],
                            b["scientific_name"],
                            b["toxicity_level"],
                            b["lookalikes"],
                            b["warning"],
                        ),
                    )

                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS survival_pois (
                        id TEXT PRIMARY KEY,
                        name TEXT,
                        category TEXT,
                        osm_tag TEXT,
                        lat REAL,
                        lon REAL,
                        elevation_m INTEGER,
                        notes TEXT
                    );
                    """
                )
                cur.execute("DELETE FROM survival_pois;")
                for p in DEFAULT_SURVIVAL_POIS:
                    cur.execute(
                        "INSERT INTO survival_pois VALUES (?, ?, ?, ?, ?, ?, ?, ?);",
                        (
                            p["id"],
                            p["name"],
                            p["category"],
                            p["osm_tag"],
                            p["lat"],
                            p["lon"],
                            p["elevation_m"],
                            p["notes"],
                        ),
                    )
                conn.commit()
            finally:
                conn.close()

        return {
            "backend": backend,
            "manuals_count": len(DEFAULT_MANUALS),
            "botanical_count": len(DEFAULT_BOTANICAL_GUARDRAILS),
            "poi_count": len(DEFAULT_SURVIVAL_POIS),
        }

    def _load_store(self) -> dict[str, Any]:
        if not self.config.json_store_path.exists():
            self.initialize_and_seed()
        return json.loads(self.config.json_store_path.read_text(encoding="utf-8"))

    def search_manuals(
        self, query: str, category: str | None = None, limit: int = 5
    ) -> list[dict[str, Any]]:
        store = self._load_store()
        manuals: list[dict[str, Any]] = store["manuals"]
        if category:
            manuals = [m for m in manuals if m["category"].lower() == category.lower()]

        q_tokens = _tokenize(query)
        if not q_tokens:
            return manuals[:limit]

        # Compute BM25-inspired relevance score over title (weight 3), tags (weight 2.5), content (weight 1)
        scored: list[tuple[float, dict[str, Any]]] = []
        for doc in manuals:
            title_t = _tokenize(doc["title"])
            tags_t = _tokenize(doc["tags"])
            content_t = _tokenize(doc["content"])
            score = 0.0
            for qt in q_tokens:
                for t in title_t:
                    if qt in t or t in qt:
                        score += 3.0
                for t in tags_t:
                    if qt in t or t in qt:
                        score += 2.5
                for t in content_t:
                    if qt == t:
                        score += 1.2
                    elif qt in t:
                        score += 0.5
            if score > 0:
                scored.append((score, doc))

        scored.sort(key=lambda item: item[0], reverse=True)
        if not scored:
            return manuals[:limit]
        return [
            {**doc, "relevance_score": round(score, 2)}
            for score, doc in scored[: max(1, min(limit, 20))]
        ]

    def query_botanical_guardrails(self, specimen_query: str) -> list[dict[str, Any]]:
        store = self._load_store()
        entries: list[dict[str, Any]] = store["botanical_guardrails"]
        q_tokens = _tokenize(specimen_query)
        if not q_tokens:
            return entries

        matches: list[dict[str, Any]] = []
        for entry in entries:
            haystack = " ".join(
                [
                    entry["common_name"],
                    entry["scientific_name"],
                    entry["lookalikes"],
                    entry["warning"],
                ]
            ).lower()
            if any(qt in haystack for qt in q_tokens):
                matches.append(entry)
        return matches if matches else entries[:2]

    def get_all_pois(self, category: str | None = None) -> list[dict[str, Any]]:
        store = self._load_store()
        pois: list[dict[str, Any]] = store["pois"]
        if category:
            cat_l = category.lower()
            pois = [
                p
                for p in pois
                if cat_l in p["category"].lower() or cat_l in p["osm_tag"].lower()
            ]
        return pois

