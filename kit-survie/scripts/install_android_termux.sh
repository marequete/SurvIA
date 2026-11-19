#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# Script d'installation 100% Hors-Ligne (après setup) pour Smartphone Android
# Cible : Termux (ARM64) + Gemma 4 E4B IT + Kiwix + Organic Maps APK
# ==============================================================================
set -e

echo "=================================================================="
echo "  INSTALLATION DU KIT DE SURVIE MCP (ANDROID + GEMMA 4 E4B IT)    "
echo "=================================================================="

if [ -n "$TERMUX_VERSION" ]; then
  echo "[1/4] Mise à jour des paquets Termux (Python, SQLite, Kiwix-Tools, Git)..."
  pkg update -y
  pkg install -y python sqlite kiwix-tools git curl
else
  echo "[1/4] Environnement Linux/Desktop détecté."
fi

echo "[2/4] Initialisation de la base SQLite FTS5 & Index Géospatial OSM..."
PYTHONPATH=./src python3 -c "
from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase
cfg = SurvivalConfig()
stats = SurvivalDatabase(cfg).initialize_and_seed()
print('Base initialisée :', stats)
"

echo "[3/4] Création du script de lancement rapide (start-android-mcp.sh)..."
cat << 'EOF' > start-android-mcp.sh
#!/usr/bin/env bash
export TARGET_AGENT_MODEL="gemma-4-e4b-it"
export MEDGEMMA_MODE="shared_vision"
export KIWIX_SERVER_URL="http://127.0.0.1:8081"
export PYTHONPATH="$(pwd)/src"

# Démarre kiwix-serve en tâche de fond si des fichiers .zim sont présents dans data/zim/
if compgen -G "data/zim/*.zim" > /dev/null; then
  echo "[Kiwix] Démarrage de kiwix-serve sur 127.0.0.1:8081..."
  kiwix-serve --port=8081 data/zim/*.zim &
fi

echo "[MCP] Démarrage du serveur Offline Survival MCP sur http://127.0.0.1:8000/mcp ..."
python3 -m offline_survival_mcp.server --transport http --host 127.0.0.1 --port 8000
EOF
chmod +x start-android-mcp.sh

echo "[4/4] Installation terminée !"
echo "-> Pour lancer en mode stdio (client MCP) : PYTHONPATH=./src python3 -m offline_survival_mcp.server"
echo "-> Pour lancer en mode HTTP local Android : ./start-android-mcp.sh"

