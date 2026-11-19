import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.domains.organicmaps_domain import (
    geo_compute_offline_route,
    geo_open_organic_maps,
    geo_search_survival_poi,
)


class TestOrganicMapsDomain(unittest.TestCase):
    def test_geo_invalid_coordinates_validation(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            res = geo_search_survival_poi(lat=125.0, lon=5.7, radius_km=10.0, config=cfg)
            self.assertEqual(res["status"], "error")
            self.assertIn("Latitude", res["error"])

    def test_geo_search_and_naismith_routing_and_intent(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            cfg = SurvivalConfig(data_dir=Path(tmpdir))
            # Search water near Grenoble / Vercors sample coordinates
            pois = geo_search_survival_poi(
                lat=45.1850,
                lon=5.7200,
                radius_km=15.0,
                category="drinking_water",
                config=cfg,
            )
            self.assertEqual(pois["status"], "ok")
            self.assertTrue(len(pois["pois"]) >= 1)
            self.assertLessEqual(pois["pois"][0]["distance_km"], 15.0)
            self.assertIn("compass_cardinal", pois["pois"][0])

            # Route computation with Naismith hiking time & compass bearing
            route = geo_compute_offline_route(
                start_lat=45.1885,
                start_lon=5.7245,
                end_lat=45.1620,
                end_lon=5.6980,
                mode="walk",
                config=cfg,
            )
            self.assertEqual(route["status"], "ok")
            self.assertGreater(route["straight_distance_km"], 2.0)
            self.assertGreater(route["estimated_trail_distance_km"], route["straight_distance_km"])
            self.assertGreater(route["estimated_duration_minutes"], 30)

            # Android Organic Maps URI & Intent builder (without executing am start in test)
            intent_res = geo_open_organic_maps(
                lat=45.1885,
                lon=5.7245,
                label="Source Eau Potable",
                launch_intent=False,
                config=cfg,
            )
            self.assertIn("om://", intent_res["organic_maps_uri"])
            self.assertIn("geo:45.1885,5.7245", intent_res["android_geo_uri"])
            self.assertIn("app.organicmaps", intent_res["termux_am_command"])


if __name__ == "__main__":
    unittest.main()

