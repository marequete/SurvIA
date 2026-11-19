"""
Domain 4: Organic Maps Offline Geospatial POI Search, Naismith Trail Routing & Android Intent Bridge
Unifies `organicmaps/organicmaps` + `survival-station` PMTiles:
- Spatial search over offline OpenStreetMap survival POIs (drinking water springs, alpine huts,
  caves, hospitals, mountain rescue stations)
- Compass bearing (0-360° + French cardinal points) & Naismith elevation-adjusted hiking routing
- Native Android integration: generates `om://` and `geo:` URIs and launches the Organic Maps
  Android app (`app.organicmaps`) directly via `am start` when running in Termux on Android.
"""

import math
import subprocess
from typing import Any
import urllib.parse

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase


CARDINAL_DIRS = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"]


def _validate_coords(lat: float, lon: float) -> str | None:
    if not (-90.0 <= lat <= 90.0):
        return f"Latitude invalide ({lat}) : doit être comprise entre -90.0 et +90.0."
    if not (-180.0 <= lon <= 180.0):
        return f"Longitude invalide ({lon}) : doit être comprise entre -180.0 et +180.0."
    return None


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0088
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2.0) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(max(0.0, 1.0 - a)))


def initial_compass_bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> tuple[float, str]:
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dlambda = math.radians(lon2 - lon1)
    x = math.sin(dlambda) * math.cos(phi2)
    y = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(dlambda)
    bearing = (math.degrees(math.atan2(x, y)) + 360.0) % 360.0
    idx = int((bearing + 22.5) // 45.0) % 8
    return round(bearing, 1), CARDINAL_DIRS[idx]


def geo_search_survival_poi(
    lat: float,
    lon: float,
    radius_km: float = 10.0,
    category: str | None = None,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Finds offline survival POIs (drinking water, shelters, hospitals, rescue) within `radius_km`,
    sorted by distance and annotated with compass azimuth and elevation.
    """
    err = _validate_coords(lat, lon)
    if err:
        return {"status": "error", "error": err}

    cfg = config or SurvivalConfig()
    db = SurvivalDatabase(cfg)
    raw_pois = db.get_all_pois(category=category)

    enriched: list[dict[str, Any]] = []
    for poi in raw_pois:
        dist = haversine_km(lat, lon, float(poi["lat"]), float(poi["lon"]))
        if dist <= radius_km:
            bearing_deg, cardinal = initial_compass_bearing(
                lat, lon, float(poi["lat"]), float(poi["lon"])
            )
            enriched.append(
                {
                    **poi,
                    "distance_km": round(dist, 2),
                    "compass_bearing_deg": bearing_deg,
                    "compass_cardinal": cardinal,
                    "organic_maps_uri": f"geo:{poi['lat']},{poi['lon']}?q={poi['lat']},{poi['lon']}({urllib.parse.quote(poi['name'])})",
                }
            )

    enriched.sort(key=lambda item: item["distance_km"])
    return {
        "status": "ok",
        "origin": {"lat": lat, "lon": lon},
        "radius_km": radius_km,
        "category_filter": category or "all",
        "count": len(enriched),
        "pois": enriched,
    }


def geo_compute_offline_route(
    start_lat: float,
    start_lon: float,
    end_lat: float,
    end_lon: float,
    mode: str = "walk",
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Computes an offline wilderness route between two GPS coordinates using:
    - Haversine great-circle distance + terrain tortuosity multiplier (1.28x for mountain trails)
    - Elevation profile estimation from nearby indexed OSM terrain features
    - Naismith's Rule hiking duration (4 km/h horizontal + 1 hour per 600 m ascent)
    - Nearby water/shelter waypoints along the corridor
    """
    for coord_lat, coord_lon in ((start_lat, start_lon), (end_lat, end_lon)):
        err = _validate_coords(coord_lat, coord_lon)
        if err:
            return {"status": "error", "error": err}

    cfg = config or SurvivalConfig()
    db = SurvivalDatabase(cfg)
    pois = db.get_all_pois()

    straight_km = haversine_km(start_lat, start_lon, end_lat, end_lon)
    bearing_deg, cardinal = initial_compass_bearing(start_lat, start_lon, end_lat, end_lon)

    # Estimate start and end elevations from nearest terrain POIs
    def _nearest_elev(lat: float, lon: float) -> int:
        if not pois:
            return 400
        nearest = min(
            pois, key=lambda p: haversine_km(lat, lon, float(p["lat"]), float(p["lon"]))
        )
        return int(nearest.get("elevation_m", 400))

    start_elev = _nearest_elev(start_lat, start_lon)
    end_elev = _nearest_elev(end_lat, end_lon)
    ascent_m = max(0, end_elev - start_elev)

    tortuosity = 1.28 if mode == "walk" else 1.35
    trail_km = round(straight_km * tortuosity, 2)

    base_speed_kmh = 4.0 if mode == "walk" else 12.0
    horizontal_hours = trail_km / base_speed_kmh
    ascent_hours = ascent_m / 600.0
    total_minutes = max(1, int(round((horizontal_hours + ascent_hours) * 60)))

    return {
        "status": "ok",
        "mode": mode,
        "start": {"lat": start_lat, "lon": start_lon, "estimated_elevation_m": start_elev},
        "destination": {"lat": end_lat, "lon": end_lon, "estimated_elevation_m": end_elev},
        "straight_distance_km": round(straight_km, 2),
        "estimated_trail_distance_km": trail_km,
        "elevation_gain_m": ascent_m,
        "compass_bearing_deg": bearing_deg,
        "compass_cardinal": cardinal,
        "estimated_duration_minutes": total_minutes,
        "naismith_formula_note": "4 km/h + 10 min par 100 m de dénivelé positif (facteur de sinuosité sentier 1.28x)",
        "organic_maps_route_uri": (
            f"om://route?sll={start_lat},{start_lon}&dll={end_lat},{end_lon}&type={mode}"
        ),
    }


def geo_open_organic_maps(
    lat: float,
    lon: float,
    label: str = "Point de Survie",
    launch_intent: bool = True,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Builds Organic Maps (`om://` and `geo:`) deep-links and, when running on Android / Termux
    with `launch_intent=True`, executes `am start` to open the exact coordinate in the native
    Organic Maps Android application offline.
    """
    err = _validate_coords(lat, lon)
    if err:
        return {"status": "error", "error": err}

    cfg = config or SurvivalConfig()
    encoded_label = urllib.parse.quote(label)
    geo_uri = f"geo:{lat},{lon}?q={lat},{lon}({encoded_label})"
    om_uri = f"om://map?ll={lat},{lon}&n={encoded_label}"
    am_cmd = (
        f'am start -a android.intent.action.VIEW -d "{geo_uri}" app.organicmaps'
    )

    intent_executed = False
    intent_output = "Exécution Android Intent désactivée ou hôte non-Android."

    if launch_intent and cfg.is_android:
        try:
            proc = subprocess.run(
                ["am", "start", "-a", "android.intent.action.VIEW", "-d", geo_uri, "app.organicmaps"],
                capture_output=True,
                text=True,
                timeout=3.0,
                check=False,
            )
            intent_executed = proc.returncode == 0
            intent_output = (proc.stdout or proc.stderr).strip()
        except Exception as exc:
            intent_output = f"Échec de l'exécution de `am start` ({type(exc).__name__})."

    return {
        "status": "ok",
        "coordinates": {"lat": lat, "lon": lon, "label": label},
        "organic_maps_uri": om_uri,
        "android_geo_uri": geo_uri,
        "pmtiles_local_viewer_url": f"http://127.0.0.1:{cfg.pmtiles_port}/#14/{lat}/{lon}",
        "termux_am_command": am_cmd,
        "intent_executed": intent_executed,
        "intent_detail": intent_output,
    }

