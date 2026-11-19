import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.domains.kiwix_domain import (
    kiwix_get_article,
    kiwix_list_libraries,
    kiwix_search,
    strip_kiwix_html,
)


class TestKiwixDomain(unittest.TestCase):
    def test_html_stripper_removes_scripts_and_tags(self):
        raw_html = """
        <html>
          <head><style>body { color: red; }</style><script>alert(1);</script></head>
          <body>
            <h1>Purification de l'eau</h1>
            <p>Faire <b>bouillir</b> l'eau pendant 1 à 3 minutes.</p>
          </body>
        </html>
        """
        cleaned = strip_kiwix_html(raw_html)
        self.assertIn("Purification de l'eau", cleaned)
        self.assertIn("Faire bouillir l'eau pendant 1 à 3 minutes.", cleaned)
        self.assertNotIn("alert(1)", cleaned)
        self.assertNotIn("color: red", cleaned)

    def test_kiwix_fallback_when_server_down(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            # Port 65531 is unused -> forces Circuit Breaker fallback to local survival DB
            cfg = SurvivalConfig(
                data_dir=Path(tmpdir),
                kiwix_base_url="http://127.0.0.1:65531",
                http_timeout_sec=0.2,
            )
            libs = kiwix_list_libraries(cfg)
            self.assertEqual(libs["status"], "fallback_offline_catalog")
            self.assertTrue(len(libs["libraries"]) >= 3)

            search_res = kiwix_search("eau ébullition", library="wikimed", limit=3, config=cfg)
            self.assertEqual(search_res["mode"], "circuit_breaker_local_fts")
            self.assertTrue(len(search_res["results"]) >= 1)

            article_res = kiwix_get_article("/local/manual-water-01", max_chars=1500, config=cfg)
            self.assertIn("ÉBULLITION", article_res["content"])


if __name__ == "__main__":
    unittest.main()

