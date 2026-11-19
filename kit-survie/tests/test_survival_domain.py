import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.domains.survival_domain import (
    survival_emergency_protocol,
    survival_search_manuals,
    survival_verify_edibility,
)
from offline_survival_mcp.services.pmtiles_server import extract_byte_range


class TestSurvivalDomain(unittest.TestCase):
    def test_edibility_guardrail_blocks_wild_fungi(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            res = survival_verify_edibility(
                specimen_name="Amanite ou champignon blanc à lamelles",
                characteristics="Chapeau blanc, anneau et volve à la base",
                config=cfg,
            )
            self.assertFalse(res["safe_to_ingest"])
            self.assertEqual(res["guardrail_status"], "STRICT_BLOCK_NEVER_CONSUME_UNVERIFIED")
            self.assertIn("NE JAMAIS", res["mandatory_safety_directive"].upper())
            self.assertTrue(len(res["matched_hazards"]) >= 1)

    def test_emergency_protocol_and_manuals(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            proto = survival_emergency_protocol("hypothermia", config=cfg)
            self.assertEqual(proto["scenario"], "hypothermia")
            self.assertTrue(len(proto["immediate_steps"]) >= 3)

            manuals = survival_search_manuals("solaire mppt radio", category=None, limit=2, config=cfg)
            self.assertTrue(len(manuals["results"]) >= 1)

    def test_pmtiles_byte_range_serving(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            sample = Path(tmpdir) / "region.pmtiles"
            sample.write_bytes(b"PMTILES_V3_HEADER_0123456789ABCDEF")
            status, headers, chunk = extract_byte_range(sample, "bytes=0-16")
            self.assertEqual(status, 206)
            self.assertEqual(chunk, b"PMTILES_V3_HEADER")
            self.assertEqual(headers["Content-Range"], "bytes 0-16/34")


if __name__ == "__main__":
    unittest.main()

