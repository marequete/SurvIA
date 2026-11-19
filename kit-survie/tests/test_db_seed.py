import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase


class TestDatabaseSeed(unittest.TestCase):
    def test_init_and_seed_databases(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            db = SurvivalDatabase(cfg)
            stats = db.initialize_and_seed()

            self.assertGreaterEqual(stats["manuals_count"], 6)
            self.assertGreaterEqual(stats["botanical_count"], 5)
            self.assertGreaterEqual(stats["poi_count"], 6)

            # Verify BM25 / FTS search works on survival manuals
            results = db.search_manuals("purification eau ébullition", limit=3)
            self.assertTrue(len(results) >= 1)
            self.assertIn("eau", results[0]["title"].lower() + " " + results[0]["content"].lower())


if __name__ == "__main__":
    unittest.main()

