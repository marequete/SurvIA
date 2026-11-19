import json
import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.server import OfflineSurvivalMCPServer


class TestUnifiedMCPServer(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.cfg = SurvivalConfig(
            data_dir=Path(self.tmpdir.name),
            kiwix_base_url="http://127.0.0.1:65531",
            http_timeout_sec=0.15,
        )
        self.server = OfflineSurvivalMCPServer(self.cfg)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_mcp_initialize_and_tools_list(self):
        init_resp = self.server.handle_jsonrpc(
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}}
        )
        self.assertEqual(init_resp["result"]["serverInfo"]["name"], "offline-survival-mcp")

        tools_resp = self.server.handle_jsonrpc(
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}
        )
        tools = tools_resp["result"]["tools"]
        self.assertEqual(len(tools), 12)
        names = {t["name"] for t in tools}
        self.assertIn("kiwix_search", names)
        self.assertIn("survival_verify_edibility", names)
        self.assertIn("medical_triage_assessment", names)
        self.assertIn("geo_search_survival_poi", names)
        self.assertIn("geo_open_organic_maps", names)
        self.assertIn("system_health_status", names)

    def test_mcp_tool_calls_across_all_four_repos(self):
        # 1. Kiwix tool call
        r_kiwix = self.server.handle_jsonrpc(
            {
                "jsonrpc": "2.0",
                "id": 10,
                "method": "tools/call",
                "params": {"name": "kiwix_search", "arguments": {"query": "eau purification"}},
            }
        )
        self.assertFalse(r_kiwix["result"]["isError"])
        payload_kiwix = json.loads(r_kiwix["result"]["content"][0]["text"])
        self.assertTrue(len(payload_kiwix["results"]) >= 1)

        # 2. Survival Station guardrail call
        r_surv = self.server.handle_jsonrpc(
            {
                "jsonrpc": "2.0",
                "id": 11,
                "method": "tools/call",
                "params": {
                    "name": "survival_verify_edibility",
                    "arguments": {"specimen_name": "Amanita phalloides"},
                },
            }
        )
        payload_surv = json.loads(r_surv["result"]["content"][0]["text"])
        self.assertFalse(payload_surv["safe_to_ingest"])

        # 3. MedGemma triage call
        r_med = self.server.handle_jsonrpc(
            {
                "jsonrpc": "2.0",
                "id": 12,
                "method": "tools/call",
                "params": {
                    "name": "medical_triage_assessment",
                    "arguments": {"symptoms": "hémorragie artérielle jambe"},
                },
            }
        )
        payload_med = json.loads(r_med["result"]["content"][0]["text"])
        self.assertEqual(payload_med["triage_level"], "RED_IMMEDIATE")

        # 4. Organic Maps geospatial call
        r_geo = self.server.handle_jsonrpc(
            {
                "jsonrpc": "2.0",
                "id": 13,
                "method": "tools/call",
                "params": {
                    "name": "geo_search_survival_poi",
                    "arguments": {"lat": 45.1885, "lon": 5.7245, "radius_km": 20.0},
                },
            }
        )
        payload_geo = json.loads(r_geo["result"]["content"][0]["text"])
        self.assertEqual(payload_geo["status"], "ok")
        self.assertTrue(payload_geo["count"] >= 1)


if __name__ == "__main__":
    unittest.main()

