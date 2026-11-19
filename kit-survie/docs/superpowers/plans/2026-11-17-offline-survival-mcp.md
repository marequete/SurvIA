# Plan de mise en œuvre de `offline-survival-mcp` (Serveur MCP de Survie Hors-Ligne Unifié)

**Objectif :** Construire un dépôt GitHub complet et autonome qui unifie `kiwix-wiki-mcp-server`, `MedGemmaImpactChallenge`, `survival-station` et `organicmaps` en un seul serveur MCP 100 % hors-ligne optimisé pour Android (`Gemma 4 E4B IT`) et PC.

**Architecture :** Architecture modulaire DDD à 4 contextes délimités (`kiwix`, `medgemma`, `survival_manuals`, `organicmaps_geo`) exposée via un serveur MCP unifié compatible `stdio` et `HTTP/SSE` (`127.0.0.1:8000`). Inclut des bases SQLite embarquées (FTS5 BM25 pour les manuels et garde-fous botaniques, index géospatial pour les POI de survie et le routage), un serveur de tuiles PMTiles hors-ligne et l'intégration native Android Intent (`om://` / `geo:`) vers Organic Maps.

**Pile technique (Tech Stack) :** Python 3.10+ (stdlib + `mcp`/`fastmcp` compatible + SQLite FTS5) & TypeScript/Node.js runner hybride, HTML5/Tailwind CSS v4/Three.js (Dashboard tactique 3D hors-ligne), Shell/Termux pour Android.

**Spécification :** `docs/superpowers/specs/2026-11-17-offline-survival-mcp-design.md`

## Contraintes globales
- **Zéro dépendance réseau externe** lors de l'exécution des outils MCP : tous les endpoints pointent sur `127.0.0.1` ou des bases SQLite locales.
- **Compatibilité Android (Termux ARM64) & Gemma 4 E4B IT** : pas de dépendance PowerShell obligatoire ; empreinte RAM du serveur MCP < 80 Mo ; mode `shared_vision` par défaut pour éviter de charger un second modèle de 4B en RAM à côté de `Gemma 4 E4B IT`.
- **Garde-fou vital obligatoire** : toute requête sur la comestibilité d'une plante ou d'un champignon sauvage doit déclencher le verrou de sécurité issu de `survival-station`.

## Focus de révision
1. **Panne de `kiwix-serve` (`127.0.0.1:8081` éteint)** : `kiwix_search` doit basculer gracieusement (Circuit Breaker) sur la base SQLite FTS5 locale sans lever d'exception non gérée (Test : `test_kiwix_fallback_when_server_down`).
2. **Tentative de validation d'ingestion d'une plante ou d'un champignon toxique/inconnu** : `survival_verify_edibility` doit retourner `SAFE_TO_INGEST: FALSE` et l'avertissement de sécurité obligatoire (Test : `test_edibility_guardrail_blocks_wild_fungi`).
3. **Saturation RAM sur Android lors d'une analyse d'image médicale** : `medical_analyze_injury_image` en mode `shared_vision` doit préparer le dossier clinique structuré *MedGemma-FA* sans exiger qu'un second serveur `llama-server` soit actif (Test : `test_medical_shared_vision_mode_for_gemma4`).
4. **Coordonnées GPS invalides ou hors bornes** : `geo_search_survival_poi` et `geo_compute_offline_route` doivent rejeter proprement les latitudes hors `[-90, 90]` ou longitudes hors `[-180, 180]` (Test : `test_geo_invalid_coordinates_validation`).
5. **Requête HTTP `Range` sur fichier `.pmtiles` hors-ligne** : le serveur PMTiles doit supporter le code `206 Partial Content` pour le visualiseur MapLibre local (Test : `test_pmtiles_byte_range_serving`).

---

### Tâche 1 : Socle de Base de Données Hors-Ligne (SQLite FTS5 Manuels + Géospatial OSM)

**Fichiers :**
- Créer : `src/offline_survival_mcp/db/schema_and_seed.py`
- Créer : `src/offline_survival_mcp/config.py`
- Test : `tests/test_db_seed.py`

**Interfaces :**
- Consomme : Chemins de base de données depuis `SurvivalConfig` (`config.py`)
- Produit : `init_databases(config: SurvivalConfig) -> dict[str, Path]`

- [x] **Étape 1 : Écrire le test qui échoue** (`tests/test_db_seed.py` vérifiant la création des tables FTS5 `manuals_fts`, `botanical_guardrails` et `survival_pois`).
- [x] **Étape 2 : Exécuter le test pour vérifier qu'il échoue**
- [x] **Étape 3 : Implémenter la fonction** (`init_databases` avec jeu de données initial de survie : *Where There Is No Doctor*, *US Army FM 21-76*, fiches plantes toxiques, guides iFixit/solaire et POI géospatiaux de secours).
- [x] **Étape 4 : Exécuter le test pour vérifier qu'il passe**
- [x] **Étape 5 : Commit**

---

### Tâche 2 : Domaine 1 (`kiwix-wiki-mcp-server`) — Connecteur Kiwix ZIM & Circuit Breaker

**Fichiers :**
- Créer : `src/offline_survival_mcp/domains/kiwix_domain.py`
- Test : `tests/test_kiwix_domain.py`

**Interfaces :**
- Consomme : `SurvivalConfig`
- Produit :
  - `kiwix_list_libraries(config: SurvivalConfig) -> dict`
  - `kiwix_search(query: str, library: str | None, limit: int, config: SurvivalConfig) -> dict`
  - `kiwix_get_article(url: str, max_chars: int, config: SurvivalConfig) -> dict`

- [x] **Étape 1 : Écrire le test qui échoue** (`test_kiwix_fallback_when_server_down` et `test_html_stripper`).
- [x] **Étape 2 : Exécuter le test pour vérifier qu'il échoue**
- [x] **Étape 3 : Implémenter le module** (requêtes OPDS/HTML vers `kiwix-serve`, nettoyage HTML sans dépendance lourde, et fallback SQLite FTS5 automatique).
- [x] **Étape 4 : Exécuter le test pour vérifier qu'il passe**
- [x] **Étape 5 : Commit**

---

### Tâche 3 : Domaine 2 (`survival-station`) — RAG Manuels FTS5, Garde-fous Botaniques & Serveur PMTiles

**Fichiers :**
- Créer : `src/offline_survival_mcp/domains/survival_domain.py`
- Créer : `src/offline_survival_mcp/services/pmtiles_server.py`
- Test : `tests/test_survival_domain.py`

**Interfaces :**
- Consomme : `SurvivalConfig`
- Produit :
  - `survival_search_manuals(query: str, category: str | None, limit: int, config: SurvivalConfig) -> dict`
  - `survival_verify_edibility(specimen_name: str, characteristics: str | None, config: SurvivalConfig) -> dict`
  - `survival_emergency_protocol(scenario: str, config: SurvivalConfig) -> dict`

- [x] **Étape 1 : Écrire le test qui échoue** (`test_edibility_guardrail_blocks_wild_fungi` et `test_pmtiles_byte_range_serving`).
- [x] **Étape 2 : Exécuter le test pour vérifier qu'il échoue**
- [x] **Étape 3 : Implémenter le module** (recherche BM25 FTS5, verrou de sécurité plantes/champignons, et serveur HTTP `206 Partial Content` pour `.pmtiles`).
- [x] **Étape 4 : Exécuter le test pour vérifier qu'il passe**
- [x] **Étape 5 : Commit**

---

### Tâche 4 : Domaine 3 (`MedGemmaImpactChallenge`) — Triage Premiers Secours & Vision Mutualisée `Gemma 4 E4B IT`

**Fichiers :**
- Créer : `src/offline_survival_mcp/domains/medgemma_domain.py`
- Test : `tests/test_medgemma_domain.py`

**Interfaces :**
- Consomme : `SurvivalConfig`
- Produit :
  - `medical_triage_assessment(symptoms: str, age_group: str, mechanism: str | None, config: SurvivalConfig) -> dict`
  - `medical_analyze_injury_image(image_path: str, clinical_context: str | None, config: SurvivalConfig) -> dict`

- [x] **Étape 1 : Écrire le test qui échoue** (`test_medical_shared_vision_mode_for_gemma4`).
- [x] **Étape 2 : Exécuter le test pour vérifier qu'il échoue**
- [x] **Étape 3 : Implémenter le module** (arbre de triage MARCH/ABCDE, détection de drapeaux rouges, mode `shared_vision` pour `Gemma 4 E4B IT` et client `llama-server` GGUF optionnel).
- [x] **Étape 4 : Exécuter le test pour vérifier qu'il passe**
- [x] **Étape 5 : Commit**

---

### Tâche 5 : Domaine 4 (`organicmaps`) — Recherche POI Hors-Ligne, Routage Naismith/Azimut & Intent Android

**Fichiers :**
- Créer : `src/offline_survival_mcp/domains/organicmaps_domain.py`
- Test : `tests/test_organicmaps_domain.py`

**Interfaces :**
- Consomme : `SurvivalConfig`
- Produit :
  - `geo_search_survival_poi(lat: float, lon: float, radius_km: float, category: str | None, config: SurvivalConfig) -> dict`
  - `geo_compute_offline_route(start_lat: float, start_lon: float, end_lat: float, end_lon: float, mode: str, config: SurvivalConfig) -> dict`
  - `geo_open_organic_maps(lat: float, lon: float, label: str, launch_intent: bool, config: SurvivalConfig) -> dict`

- [x] **Étape 1 : Écrire le test qui échoue** (`test_geo_invalid_coordinates_validation` et `test_geo_route_and_intent`).
- [x] **Étape 2 : Exécuter le test pour vérifier qu'il échoue**
- [x] **Étape 3 : Implémenter le module** (calcul Haversine, azimut boussole, formule de marche de Naismith avec dénivelé, génération d'URI `om://` et `geo:` + exécution Termux `am start`).
- [x] **Étape 4 : Exécuter le test pour vérifier qu'il passe**
- [x] **Étape 5 : Commit**

---

### Tâche 6 : Serveur MCP Unifié (Python + Pont Node.js Universel), Dashboard Tactique 3D, Scripts Android Termux & Compétence Agent

**Fichiers :**
- Créer : `src/offline_survival_mcp/server.py`
- Créer : `bin/mcp-server.mjs` (Runner MCP universel Node.js + Python garantissant l'exécution immédiate sur tout poste)
- Créer : `scripts/install_android_termux.sh`
- Créer : `scripts/start_survival_station.py`
- Créer : `web/index.html` (Dashboard Tactique Hors-Ligne + Boussole/Topographie 3D Three.js + Animate.css + Tailwind)
- Créer : `.agents/skills/survie-hors-ligne-mcp/SKILL.md`
- Créer : `README.md`, `pyproject.toml`, `package.json`, `.gitignore`

