"""
Idempotent Offline Survival Appliance Launcher (`scripts/start_survival_station.py`)
Inspired by `JoshKappler/survival-station`:
1. Initializes and verifies the offline survival database (`data/survival_knowledge.db`).
2. Starts the pure-Python PMTiles map server on `127.0.0.1:8082`.
3. Starts or verifies the Unified MCP Server (`stdio` or `http://127.0.0.1:8000`).
"""

import argparse
from pathlib import Path
import sys
import threading

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase
from offline_survival_mcp.server import OfflineSurvivalMCPServer
from offline_survival_mcp.services.pmtiles_server import create_pmtiles_server


def main() -> None:
    parser = argparse.ArgumentParser(description="Orchestrateur Hors-Ligne Survival Station + MCP")
    parser.add_argument("--check-only", action="store_true", help="Initialise la BDD et affiche le diagnostic de santé.")
    parser.add_argument("--transport", choices=["stdio", "http"], default="http")
    args = parser.parse_args()

    cfg = SurvivalConfig()
    db = SurvivalDatabase(cfg)
    stats = db.initialize_and_seed()
    srv = OfflineSurvivalMCPServer(cfg)

    if args.check_only:
        import json
        health = srv.call_tool("system_health_status", {})
        print(json.dumps(health, ensure_ascii=False, indent=2))
        return

    # Start background PMTiles server on 127.0.0.1:8082
    pmtiles_httpd = create_pmtiles_server(cfg.pmtiles_dir, host="127.0.0.1", port=cfg.pmtiles_port)
    pmtiles_thread = threading.Thread(target=pmtiles_httpd.serve_forever, daemon=True)
    pmtiles_thread.start()

    if args.transport == "http":
        srv.run_http(host="127.0.0.1", port=cfg.mcp_http_port)
    else:
        srv.run_stdio()


if __name__ == "__main__":
    main()

