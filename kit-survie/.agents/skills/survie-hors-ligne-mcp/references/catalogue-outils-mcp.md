# Catalogue Complet des 12 Outils MCP (`offline-survival-mcp`)

Ce document de référence détaille les signatures exactes des outils exposés par le serveur MCP pour l'agent **Gemma 4 E4B IT**.

## 1. Domaine Encyclopédique Kiwix (`kiwix-wiki-mcp-server`)
- **`kiwix_list_libraries()`** : Retourne la liste des archives `.zim` actives sur `127.0.0.1:8081` ou le catalogue de survie embarqué si `kiwix-serve` est arrêté.
- **`kiwix_search(query, library?, limit=5)`** : Recherche plein-texte dans les archives `.zim` avec bascule automatique (*Circuit Breaker*) sur la base SQLite locale.
- **`kiwix_get_article(url, max_chars=4000)`** : Extrait le texte pur d'un article Kiwix ou local en supprimant le balisage HTML/CSS/JS.

## 2. Domaine Manuels de Survie & Sécurité (`survival-station`)
- **`survival_search_manuals(query, category?, limit=5)`** : Recherche BM25 / FTS5 dans les manuels de survie (`water`, `medical`, `shelter`, `fire_signaling`, `electronics_radio`).
- **`survival_verify_edibility(specimen_name, characteristics?)`** : Garde-fou obligatoire bloquant toute recommandation d'ingestion de plante ou champignon sauvage.
- **`survival_emergency_protocol(scenario)`** : Retourne les étapes immédiates pour `hypothermia`, `hemorrhage`, `water_purification`, `radio_distress`.

## 3. Domaine Premiers Secours & Vision (`MedGemmaImpactChallenge`)
- **`medical_triage_assessment(symptoms, age_group="adult", mechanism?)`** : Triage médical MARCH / ABCDE avec détection des drapeaux rouges et contre-indications.
- **`medical_analyze_injury_image(image_path, clinical_context?)`** : Prépare l'analyse visuelle dermatologique/traumatologique (mode `shared_vision` pour Gemma 4 E4B IT sur Android ou `dedicated_medgemma` via `llama-server`).

## 4. Domaine Géospatial & Routage Hors-Ligne (`organicmaps`)
- **`geo_search_survival_poi(lat, lon, radius_km=10.0, category?)`** : Recherche par distance Haversine et azimut boussole des points d'eau, abris et hôpitaux.
- **`geo_compute_offline_route(start_lat, start_lon, end_lat, end_lon, mode="walk")`** : Calcule la distance sentier, le cap boussole, le dénivelé positif et la durée selon la formule de Naismith.
- **`geo_open_organic_maps(lat, lon, label="Point de Survie", launch_intent=True)`** : Génère les liens `om://` et `geo:` et déclenche `am start` sous Termux Android.
- **`system_health_status()`** : Retourne l'état complet des bases locales et des services.

