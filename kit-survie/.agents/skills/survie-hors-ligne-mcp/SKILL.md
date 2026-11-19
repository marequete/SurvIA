---
name: survie-hors-ligne-mcp
description: >-
  Utilise cette compétence lorsque l'agent IA (notamment Gemma 4 E4B IT sur Android ou un agent local) doit répondre à des questions de survie, de premiers secours médicaux, de botanique/champignons, de réparation matérielle ou d'orientation cartographique hors-ligne via le serveur MCP unifié offline-survival-mcp.
---

# Compétence de Survie Hors-Ligne Unifiée (MCP)

Cette compétence guide l'agent IA pour orchestrer en toute sécurité les **12 outils du serveur MCP `offline-survival-mcp`**, qui unifie quatre dépôts de référence :
1. **`kiwix-wiki-mcp-server`** (Encyclopédies `.zim` hors-ligne : Wikipédia, WikiMed, iFixit, StackExchange)
2. **`MedGemmaImpactChallenge`** (Triage médical de premiers secours MARCH/ABCDE et analyse visuelle de blessures)
3. **`survival-station`** (Manuels de survie SQLite FTS5, garde-fous botaniques vitaux et serveur de cartes PMTiles)
4. **`organicmaps`** (Recherche de points d'eau/abris/hôpitaux OSM hors-ligne, routage de Naismith et ouverture de l'application Android Organic Maps)

---

## Étapes et Instructions d'Orchestration

### 1. Règle d'Or de Sécurité Botanique et Mycologique (Obligatoire)
Dès que l'utilisateur pose une question sur une plante sauvage, une baie, une racine ou un champignon :
1. Appelle **obligatoirement** l'outil `survival_verify_edibility`.
2. Ne confirme **jamais** qu'un spécimen sauvage est comestible sur la base d'une photo ou d'une description textuelle.
3. Rappelle systématiquement les sosies mortels retournés par l'outil (ex. Amanite phalloïde, Grande Ciguë, If commun, Digitale).

### 2. Protocole de Triage Médical et Analyse Visuelle (`MedGemma-FA`)
Pour toute blessure, brûlure, morsure ou symptôme médical en milieu isolé :
1. Appelle `medical_triage_assessment` avec les symptômes et le mécanisme lésionnel pour obtenir la classification **MARCH / ABCDE** et les contre-indications strictes.
2. Si l'utilisateur fournit une photo sur son téléphone Android, appelle `medical_analyze_injury_image` (qui fonctionne par défaut en mode `shared_vision` pour économiser la RAM de **Gemma 4 E4B IT**).

### 3. Recherche Documentaire Croisée (Manuels FTS5 + Kiwix ZIM)
1. Interroge d'abord `survival_search_manuals` ou `survival_emergency_protocol` pour obtenir les procédures d'urgence vérifiées (*US Army FM 21-76*, *Where There Is No Doctor*).
2. Complète avec `kiwix_search` puis `kiwix_get_article` pour les schémas de réparation électronique/solaire (`ifixit`) ou les fiches encyclopédiques détaillées.

### 4. Localisation d'Eau, d'Abris et Navigation Hors-Ligne (`Organic Maps`)
Lorsqu'une position GPS (`lat`, `lon`) est disponible :
1. Appelle `geo_search_survival_poi` pour localiser les sources d'eau potable (`drinking_water`), les refuges (`shelter`) ou les postes de secours (`hospital`).
2. Appelle `geo_compute_offline_route` pour calculer l'azimut boussole, le dénivelé et le temps de marche selon la règle de Naismith.
3. Sur smartphone Android, appelle `geo_open_organic_maps` pour lancer directement le guidage hors-ligne dans l'application **Organic Maps** via Intent (`om://` / `geo:`).

---

## Références et Assistants Exécutables

- 📖 **Catalogue détaillé des 12 outils et schémas JSON** : [Catalogue des Outils MCP](references/catalogue-outils-mcp.md)
- 🛠️ **Assistant de diagnostic et de lancement** : [start_survival_station.py](../../scripts/start_survival_station.py)
- 📱 **Assistant d'installation Android Termux** : [install_android_termux.sh](../../scripts/install_android_termux.sh)

---

## Étapes de Validation et d'Auto-Diagnostic

Pour vérifier que le serveur MCP et ses bases hors-ligne sont opérationnels :
1. Exécuter la commande de diagnostic :
   ```bash
   python scripts/start_survival_station.py --check-only
   ```
2. Vérifier que la sortie JSON contient `"server": "offline-survival-mcp"` et que `manuals_count`, `botanical_count` et `poi_count` sont strictement supérieurs à zéro.
3. Exécuter la suite de tests unitaires :
   ```bash
   python -m unittest discover -s tests -v
   ```

