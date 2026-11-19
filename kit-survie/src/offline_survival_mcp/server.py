"""
Unified Offline Survival MCP Server (`offline-survival-mcp`)
Combines:
1. `jeffreyrampineda/kiwix-wiki-mcp-server` (ZIM offline encyclopedias)
2. `sarbabi/MedGemmaImpactChallenge` (MedGemma-FA first-aid triage & multimodal injury vision)
3. `JoshKappler/survival-station` (FTS5 survival manuals, botanical safety guardrails, PMTiles server)
4. `organicmaps/organicmaps` (Offline OSM survival POIs, Naismith routing & Android Intent deep-links)

Supports both:
- Standard MCP JSON-RPC 2.0 over `stdio`
- Local Loopback HTTP JSON-RPC + REST API (`http://127.0.0.1:8000/mcp`) for Android apps & Web UI
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys
from typing import Any

from offline_survival_mcp import __version__
from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import HAS_SQLITE3, SurvivalDatabase
from offline_survival_mcp.domains.kiwix_domain import (
    kiwix_get_article,
    kiwix_list_libraries,
    kiwix_search,
)
from offline_survival_mcp.domains.medgemma_domain import (
    medical_analyze_injury_image,
    medical_triage_assessment,
)
from offline_survival_mcp.domains.organicmaps_domain import (
    geo_compute_offline_route,
    geo_open_organic_maps,
    geo_search_survival_poi,
)
from offline_survival_mcp.domains.survival_domain import (
    survival_emergency_protocol,
    survival_search_manuals,
    survival_verify_edibility,
)


GEMMA4_SURVIVAL_SYSTEM_PROMPT = """Tu es un Agent IA de Survie 100% Hors-Ligne propulsé par Gemma 4 E4B IT sur Android.
Tu disposes d'un serveur MCP unifiant 4 systèmes :
1. KIWIX ZIM (`kiwix_list_libraries`, `kiwix_search`, `kiwix_get_article`) : Encyclopédies hors-ligne (Wikipédia, WikiMed, iFixit).
2. SURVIVAL-STATION (`survival_search_manuals`, `survival_verify_edibility`, `survival_emergency_protocol`) : Manuels US Army FM 21-76, Where There Is No Doctor, et garde-fous vitaux.
3. MEDGEMMA-FA (`medical_triage_assessment`, `medical_analyze_injury_image`) : Triage médical MARCH/ABCDE et analyse visuelle de blessures.
4. ORGANIC MAPS (`geo_search_survival_poi`, `geo_compute_offline_route`, `geo_open_organic_maps`) : Sources d'eau potable, refuges, hôpitaux et navigation hors-ligne.

RÈGLES DE SÉCURITÉ ABSOLUES :
- Ne déclare JAMAIS une plante sauvage, une baie ou un champignon comestible : appelle TOUJOURS `survival_verify_edibility`.
- Pour toute blessure ou symptôme médical : appelle TOUJOURS `medical_triage_assessment` (et `medical_analyze_injury_image` si une photo est fournie).
- Priorise systématiquement la Règle des 3 : Hémorragie/Air (3 min) -> Abri thermique (3 h) -> Eau bouillie/filtrée (3 jours)."""


MCP_TOOLS_SCHEMA: list[dict[str, Any]] = [
    {
        "name": "kiwix_list_libraries",
        "description": "Liste les archives encyclopédiques hors-ligne (.zim) disponibles sur kiwix-serve ou dans le catalogue de survie embarqué.",
        "inputSchema": {"type": "object", "properties": {}, "required": []},
    },
    {
        "name": "kiwix_search",
        "description": "Recherche plein-texte d'articles dans les encyclopédies hors-ligne Kiwix (Wikipédia, WikiMed, iFixit, StackExchange) avec repli automatique SQLite.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Termes de recherche"},
                "library": {"type": "string", "description": "Identifiant optionnel de la bibliothèque .zim"},
                "limit": {"type": "integer", "description": "Nombre max de résultats (défaut: 5)", "default": 5},
            },
            "required": ["query"],
        },
    },
    {
        "name": "kiwix_get_article",
        "description": "Récupère et nettoie le texte complet d'un article Kiwix ou local pour économiser la fenêtre de contexte de Gemma 4 E4B IT.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "url": {"type": "string", "description": "Chemin de l'article (ex: /A/Water_purification ou /local/manual-water-01)"},
                "max_chars": {"type": "integer", "description": "Limite de caractères retournés (défaut: 4000)", "default": 4000},
            },
            "required": ["url"],
        },
    },
    {
        "name": "survival_search_manuals",
        "description": "Recherche plein-texte (BM25 / SQLite FTS5) dans les manuels de survie hors-ligne (US Army FM 21-76, Where There Is No Doctor, Radio/Solaire).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Requête de survie (ex: 'purification eau ébullition', 'abri hypothermie', 'panne mppt solaire')"},
                "category": {
                    "type": "string",
                    "description": "Filtre optionnel : water, medical, shelter, fire_signaling, electronics_radio",
                },
                "limit": {"type": "integer", "default": 5},
            },
            "required": ["query"],
        },
    },
    {
        "name": "survival_verify_edibility",
        "description": "GARDE-FOU OBLIGATOIRE (survival-station) : Vérifie les risques mortels de plantes/champignons sauvages. Interdit toute validation d'ingestion incertaine.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "specimen_name": {"type": "string", "description": "Nom ou description de la plante, baie ou champignon"},
                "characteristics": {"type": "string", "description": "Détails observés (couleur, lamelles, ombelle, odeur, habitat)"},
            },
            "required": ["specimen_name"],
        },
    },
    {
        "name": "survival_emergency_protocol",
        "description": "Retourne une checklist d'urgence vitale immédiate (hypothermia, hemorrhage, water_purification, radio_distress).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "scenario": {"type": "string", "description": "Scénario d'urgence (ex: hypothermia, hemorrhage, water_purification, radio_distress)"},
            },
            "required": ["scenario"],
        },
    },
    {
        "name": "medical_triage_assessment",
        "description": "Exécute l'arbre de triage premiers secours MedGemma-FA (MARCH / ABCDE), détecte les drapeaux rouges et fournit les contre-indications vitales.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "symptoms": {"type": "string", "description": "Symptômes observés et état du blessé"},
                "age_group": {"type": "string", "description": "Tranche d'âge (adult, child, infant, elderly)", "default": "adult"},
                "mechanism": {"type": "string", "description": "Mécanisme lésionnel (chute, brûlure, morsure, coupure)"},
            },
            "required": ["symptoms"],
        },
    },
    {
        "name": "medical_analyze_injury_image",
        "description": "Analyse une photo de blessure/brûlure/morsure via le protocole multimodal MedGemma-FA (optimisé RAM en mode shared_vision pour Gemma 4 E4B IT sur Android).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "image_path": {"type": "string", "description": "Chemin local vers la photo de la blessure"},
                "clinical_context": {"type": "string", "description": "Contexte clinique (douleur, heure de la blessure, évolution)"},
            },
            "required": ["image_path"],
        },
    },
    {
        "name": "geo_search_survival_poi",
        "description": "Recherche hors-ligne (Organic Maps / OSM) les points vitaux autour d'une position GPS : sources d'eau potable, refuges, hôpitaux, grottes.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "lat": {"type": "number", "description": "Latitude GPS (-90 à 90)"},
                "lon": {"type": "number", "description": "Longitude GPS (-180 à 180)"},
                "radius_km": {"type": "number", "description": "Rayon de recherche en km (défaut: 10)", "default": 10.0},
                "category": {"type": "string", "description": "Filtre : drinking_water, shelter, hospital, first_aid_station"},
            },
            "required": ["lat", "lon"],
        },
    },
    {
        "name": "geo_compute_offline_route",
        "description": "Calcule un itinéraire d'évacuation ou de ravitaillement hors-ligne (distance sentier, cap boussole, dénivelé et durée selon la règle de Naismith).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "start_lat": {"type": "number"},
                "start_lon": {"type": "number"},
                "end_lat": {"type": "number"},
                "end_lon": {"type": "number"},
                "mode": {"type": "string", "description": "walk ou bicycle", "default": "walk"},
            },
            "required": ["start_lat", "start_lon", "end_lat", "end_lon"],
        },
    },
    {
        "name": "geo_open_organic_maps",
        "description": "Génère les URI hors-ligne `om://` et `geo:` et lance directement l'application Android Organic Maps (`app.organicmaps`) via Intent sur téléphone.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "lat": {"type": "number"},
                "lon": {"type": "number"},
                "label": {"type": "string", "default": "Point de Survie"},
                "launch_intent": {"type": "boolean", "default": True},
            },
            "required": ["lat", "lon"],
        },
    },
    {
        "name": "system_health_status",
        "description": "Vérifie l'état des 4 sous-systèmes hors-ligne (base SQLite FTS5/OSM, Kiwix ZIM, MedGemma / Gemma 4 E4B IT, serveur PMTiles et profil Android).",
        "inputSchema": {"type": "object", "properties": {}, "required": []},
    },
]


class OfflineSurvivalMCPServer:
    """JSON-RPC 2.0 Model Context Protocol Server uniting the 4 offline survival repositories."""

    def __init__(self, config: SurvivalConfig | None = None) -> None:
        self.config = config or SurvivalConfig()
        self.db = SurvivalDatabase(self.config)
        self.db_stats = self.db.initialize_and_seed()

    def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if name == "kiwix_list_libraries":
            return kiwix_list_libraries(self.config)
        if name == "kiwix_search":
            return kiwix_search(
                query=str(arguments.get("query", "")),
                library=arguments.get("library"),
                limit=int(arguments.get("limit", 5)),
                config=self.config,
            )
        if name == "kiwix_get_article":
            return kiwix_get_article(
                url=str(arguments.get("url", "")),
                max_chars=int(arguments.get("max_chars", 4000)),
                config=self.config,
            )
        if name == "survival_search_manuals":
            return survival_search_manuals(
                query=str(arguments.get("query", "")),
                category=arguments.get("category"),
                limit=int(arguments.get("limit", 5)),
                config=self.config,
            )
        if name == "survival_verify_edibility":
            return survival_verify_edibility(
                specimen_name=str(arguments.get("specimen_name", "")),
                characteristics=arguments.get("characteristics"),
                config=self.config,
            )
        if name == "survival_emergency_protocol":
            return survival_emergency_protocol(
                scenario=str(arguments.get("scenario", "")),
                config=self.config,
            )
        if name == "medical_triage_assessment":
            return medical_triage_assessment(
                symptoms=str(arguments.get("symptoms", "")),
                age_group=str(arguments.get("age_group", "adult")),
                mechanism=arguments.get("mechanism"),
                config=self.config,
            )
        if name == "medical_analyze_injury_image":
            return medical_analyze_injury_image(
                image_path=str(arguments.get("image_path", "")),
                clinical_context=arguments.get("clinical_context"),
                config=self.config,
            )
        if name == "geo_search_survival_poi":
            return geo_search_survival_poi(
                lat=float(arguments["lat"]),
                lon=float(arguments["lon"]),
                radius_km=float(arguments.get("radius_km", 10.0)),
                category=arguments.get("category"),
                config=self.config,
            )
        if name == "geo_compute_offline_route":
            return geo_compute_offline_route(
                start_lat=float(arguments["start_lat"]),
                start_lon=float(arguments["start_lon"]),
                end_lat=float(arguments["end_lat"]),
                end_lon=float(arguments["end_lon"]),
                mode=str(arguments.get("mode", "walk")),
                config=self.config,
            )
        if name == "geo_open_organic_maps":
            return geo_open_organic_maps(
                lat=float(arguments["lat"]),
                lon=float(arguments["lon"]),
                label=str(arguments.get("label", "Point de Survie")),
                launch_intent=bool(arguments.get("launch_intent", True)),
                config=self.config,
            )
        if name == "system_health_status":
            return {
                "server": "offline-survival-mcp",
                "version": __version__,
                "target_agent_model": self.config.target_agent_model,
                "medgemma_mode": self.config.medgemma_mode,
                "is_android_environment": self.config.is_android,
                "sqlite3_native_available": HAS_SQLITE3,
                "database_stats": self.db_stats,
                "endpoints": {
                    "kiwix_serve": self.config.kiwix_base_url,
                    "medgemma_llama_server": self.config.medgemma_server_url,
                    "pmtiles_map_server": f"http://127.0.0.1:{self.config.pmtiles_port}",
                },
                "unified_repositories": [
                    "https://github.com/jeffreyrampineda/kiwix-wiki-mcp-server.git",
                    "https://github.com/sarbabi/MedGemmaImpactChallenge.git",
                    "https://github.com/JoshKappler/survival-station.git",
                    "https://github.com/organicmaps/organicmaps.git",
                ],
            }
        raise ValueError(f"Outil MCP inconnu : {name}")

    def handle_jsonrpc(self, request: dict[str, Any]) -> dict[str, Any] | None:
        method = request.get("method", "")
        req_id = request.get("id")
        params = request.get("params") or {}

        # Notifications (no id) do not expect a response
        if req_id is None and method.startswith("notifications/"):
            return None

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {"listChanged": False},
                        "prompts": {"listChanged": False},
                    },
                    "serverInfo": {
                        "name": "offline-survival-mcp",
                        "version": __version__,
                    },
                },
            }

        if method == "ping":
            return {"jsonrpc": "2.0", "id": req_id, "result": {}}

        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": MCP_TOOLS_SCHEMA},
            }

        if method == "prompts/list":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "prompts": [
                        {
                            "name": "gemma4_survival_system_prompt",
                            "description": "Prompt système de survie hors-ligne pour Gemma 4 E4B IT sur Android avec garde-fous médicaux et botaniques.",
                        }
                    ]
                },
            }

        if method == "prompts/get":
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "description": "Prompt système Gemma 4 E4B IT Survie Hors-Ligne",
                    "messages": [
                        {
                            "role": "user",
                            "content": {"type": "text", "text": GEMMA4_SURVIVAL_SYSTEM_PROMPT},
                        }
                    ],
                },
            }

        if method == "tools/call":
            tool_name = params.get("name", "")
            tool_args = params.get("arguments") or {}
            try:
                output = self.call_tool(tool_name, tool_args)
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": json.dumps(output, ensure_ascii=False, indent=2),
                            }
                        ],
                        "isError": False,
                    },
                }
            except Exception as exc:
                return {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": json.dumps(
                                    {"error": str(exc), "tool": tool_name}, ensure_ascii=False
                                ),
                            }
                        ],
                        "isError": True,
                    },
                }

        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {"code": -32601, "message": f"Method not found: {method}"},
        }

    def run_stdio(self) -> None:
        """Runs the MCP server over standard input/output (stdio)."""
        for raw_line in sys.stdin:
            line = raw_line.strip()
            if not line:
                continue
            try:
                req = json.loads(line)
                resp = self.handle_jsonrpc(req)
                if resp is not None:
                    sys.stdout.write(json.dumps(resp, ensure_ascii=False) + "\n")
                    sys.stdout.flush()
            except Exception as exc:
                err_resp = {
                    "jsonrpc": "2.0",
                    "id": None,
                    "error": {"code": -32700, "message": f"Parse error: {exc}"},
                }
                sys.stdout.write(json.dumps(err_resp) + "\n")
                sys.stdout.flush()

    def run_http(self, host: str = "127.0.0.1", port: int | None = None) -> None:
        """Runs the MCP server over HTTP loopback (`127.0.0.1:8000/mcp` and `/health`)."""
        mcp_server = self
        bind_port = port or self.config.mcp_http_port

        class MCPHTTPHandler(BaseHTTPRequestHandler):
            def do_OPTIONS(self) -> None:
                self.send_response(204)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                self.send_header("Access-Control-Allow-Headers", "Content-Type")
                self.end_headers()

            def do_GET(self) -> None:
                if self.path.startswith("/health"):
                    payload = mcp_server.call_tool("system_health_status", {})
                    data = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json; charset=utf-8")
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(data)
                    return

                self.send_response(404)
                self.end_headers()

            def do_POST(self) -> None:
                length = int(self.headers.get("Content-Length", "0"))
                raw = self.rfile.read(length).decode("utf-8")
                req = json.loads(raw)
                resp = mcp_server.handle_jsonrpc(req) or {"status": "accepted"}
                data = json.dumps(resp, ensure_ascii=False).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, format: str, *args: object) -> None:
                return

        httpd = ThreadingHTTPServer((host, bind_port), MCPHTTPHandler)
        print(f"[offline-survival-mcp] HTTP MCP Server listening on http://{host}:{bind_port}/mcp", file=sys.stderr)
        httpd.serve_forever()


def main() -> None:
    parser = argparse.ArgumentParser(description="Offline Survival MCP Server (Android Gemma 4 E4B IT & PC)")
    parser.add_argument("--transport", choices=["stdio", "http"], default="stdio")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()

    srv = OfflineSurvivalMCPServer()
    if args.transport == "http":
        srv.run_http(host=args.host, port=args.port)
    else:
        srv.run_stdio()


if __name__ == "__main__":
    main()

