import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.domains.medgemma_domain import (
    medical_analyze_injury_image,
    medical_triage_assessment,
)


class TestMedGemmaDomain(unittest.TestCase):
    def test_medical_triage_march_abcde(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            res = medical_triage_assessment(
                symptoms="Saignement abondant à l'avant-bras après coupure sur tôle, rougeur et chaleur",
                age_group="adult",
                mechanism="lacération métallique",
                config=cfg,
            )
            self.assertEqual(res["triage_level"], "RED_IMMEDIATE")
            self.assertIn("MARCH", res["clinical_framework"])
            self.assertTrue(len(res["red_flags_detected"]) >= 1)
            self.assertTrue(len(res["contraindications"]) >= 2)

    def test_medical_shared_vision_mode_for_gemma4(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            img_path = Path(tmpdir) / "wound_sample.png"
            # Minimal valid 1x1 PNG header bytes
            img_path.write_bytes(
                b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde"
            )
            cfg = SurvivalConfig(
                data_dir=Path(tmpdir),
                target_agent_model="gemma-4-e4b-it",
                medgemma_mode="shared_vision",
            )
            res = medical_analyze_injury_image(
                image_path=str(img_path),
                clinical_context="Brûlure thermique sur la main avec cloque de 3 cm",
                config=cfg,
            )
            self.assertEqual(res["execution_mode"], "shared_vision_gemma_4_e4b_it")
            self.assertEqual(res["image_meta"]["mime_type"], "image/png")
            self.assertIn("MEDGEMMA-FA", res["clinical_vision_prompt"].upper())


if __name__ == "__main__":
    unittest.main()

