"""
Domain 3: MedGemma-FA Offline First-Aid Triage & Multimodal Injury Analysis
Unifies `sarbabi/MedGemmaImpactChallenge`:
- Structured MARCH / ABCDE wilderness medical triage
- Red-flag detection and mandatory first-aid contraindications
- Dual-mode Vision Architecture optimized for Android + Gemma 4 E4B IT:
  1. `shared_vision` (default on Android): Uses Gemma 4 E4B IT's native multimodal vision
     guided by the MedGemma-FA clinical inspection rubric (saves ~3.2 GB RAM on mobile).
  2. `dedicated_medgemma`: Queries a local `llama.cpp` (`llama-server`) instance running
     `google_medgemma-4b-it-Q4_K_M.gguf` + `mmproj-medgemma-4b-it-F16.gguf`, with automatic
     Circuit Breaker fallback to `shared_vision`.
"""

import base64
import json
import mimetypes
from pathlib import Path
from typing import Any
import urllib.request

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase


RED_FLAG_KEYWORDS: list[tuple[str, str]] = [
    ("saignement", "Hémorragie active potentielle — vérifier nécessité de compression directe / garrot (M - Massive Hemorrhage)."),
    ("hémorragie", "Risque de choc hypovolémique — priorité absolue au contrôle du saignement."),
    ("inconscient", "Altération de la conscience — vérifier la liberté des voies aériennes (A - Airway) et position latérale de sécurité."),
    ("respir", "Détresse respiratoire potentielle — vérifier pneumothorax / obstruction (R - Respiration)."),
    ("morsure", "Risque d'envenimation ou d'infection polymicrobienne (rage/tétanos) — immobilisation sans garrot artériel."),
    ("brûlure", "Risque de perte hydro-électrolytique et d'infection — refroidissement 15 min à l'eau tempérée, ne pas percer les phlyctènes."),
    ("rougeur", "Signe inflammatoire local (érythème/chaleur) — délimiter les bords au stylo pour surveiller une cellulite/lymphangite."),
    ("fracture", "Traumatisme ostéo-articulaire — vérifier le pouls distal et la sensibilité avant et après immobilisation par attelle."),
]

MEDGEMMA_FA_VISION_RUBRIC = """[GRILLE CLINIQUE MEDGEMMA-FA POUR GEMMA 4 E4B IT (MODE HORS-LIGNE)]
En tant qu'assistant médical de premiers secours hors-ligne, analyse l'image de la lésion selon ces 5 axes :
1. MORPHOLOGIE DE LA LÉSION : Type (lacération, abrasion, brûlure 1er/2e/3e degré, morsure/piqûre, éruption), taille estimée et profondeur.
2. SIGNES D'URGENCE VITALE (MARCH) : Saignement actif pulsatile ou en nappe, nécrose noirâtre, exposition tendineuse/osseuse.
3. SIGNES D'INFECTION : Érythème périphérique diffus, œdème, exsudat purulent, traînée rougeâtre (lymphangite).
4. PROTOCOLE DE SOINS LOCAUX : Irrigation (eau bouillie refroidie / sérum physiologique), antisepsie, type de pansement et immobilisation.
5. CONTRE-INDICATIONS STRICTES : Gestes à proscrire absolument en milieu isolé."""


def medical_triage_assessment(
    symptoms: str,
    age_group: str = "adult",
    mechanism: str | None = None,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Executes MedGemma-FA wilderness triage (MARCH / ABCDE) over patient symptoms
    and cross-references offline medical manuals.
    """
    cfg = config or SurvivalConfig()
    combined = f"{symptoms} {mechanism or ''}".lower()

    detected_flags: list[str] = []
    for kw, warning in RED_FLAG_KEYWORDS:
        if kw in combined:
            detected_flags.append(warning)

    is_critical = any(
        k in combined
        for k in ("saignement", "hémorragie", "inconscient", "artériel", "asphyxie", "thorax")
    )
    triage_level = "RED_IMMEDIATE" if is_critical else ("YELLOW_URGENT" if detected_flags else "GREEN_DELAYED")

    db = SurvivalDatabase(cfg)
    medical_refs = db.search_manuals(f"{symptoms} {mechanism or ''}", category="medical", limit=2)

    return {
        "triage_level": triage_level,
        "patient_age_group": age_group,
        "mechanism_of_injury": mechanism or "non spécifié",
        "clinical_framework": "Protocole MARCH / ABCDE (MedGemma-FA Wilderness First Aid)",
        "red_flags_detected": detected_flags
        or ["Aucun drapeau rouge hémorragique ou respiratoire immédiat détecté dans l'énoncé — maintenir une surveillance clinique."],
        "immediate_actions": [
            "M (Massive Hemorrhage) : Contrôler tout saignement externe par compression directe.",
            "A (Airway) & R (Respiration) : Vérifier la perméabilité des voies aériennes et la fréquence respiratoire.",
            "C (Circulation) : Vérifier le pouls radial, le temps de recoloration cutanée (< 2s) et prévenir l'état de choc.",
            "H (Hypothermia / Head) : Couvrir systématiquement le blessé (même par temps doux, le traumatisme favorise l'hypothermie).",
        ],
        "contraindications": [
            "Ne JAMAIS retirer un objet perforant profondément ancré (effet tampon sur les vaisseaux).",
            "Ne JAMAIS donner à boire ou à manger à un blessé présentant une altération de la conscience ou nécessitant une chirurgie.",
            "Ne JAMAIS inciser ou sucer une morsure venimeuse.",
        ],
        "offline_manual_references": medical_refs,
        "medical_disclaimer": (
            "Avertissement MedGemma-FA : Aide aux premiers secours en situation d'isolement hors-ligne. "
            "Ne remplace pas une prise en charge médicale dès que les secours sont joignables."
        ),
    }


def _call_dedicated_medgemma_server(
    server_url: str,
    b64_image: str,
    mime_type: str,
    clinical_context: str,
    timeout: float,
) -> str:
    payload = {
        "model": "medgemma-4b-it",
        "messages": [
            {"role": "system", "content": MEDGEMMA_FA_VISION_RUBRIC},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": clinical_context or "Analyse cette blessure pour les premiers secours."},
                    {"type": "image_url", "image_url": {"url": f"data:{mime_type};base64,{b64_image}"}},
                ],
            },
        ],
        "temperature": 0.1,
        "max_tokens": 512,
    }
    req = urllib.request.Request(
        f"{server_url.rstrip('/')}/v1/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data["choices"][0]["message"]["content"]


def medical_analyze_injury_image(
    image_path: str,
    clinical_context: str | None = None,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Prepares and analyzes an injury photograph for offline first-aid guidance.
    In `shared_vision` mode (default for Android + Gemma 4 E4B IT), returns the structured
    MedGemma-FA visual inspection protocol + image payload for Gemma 4 E4B IT.
    In `dedicated_medgemma` mode, calls `llama-server` (`mmproj-medgemma-4b-it-F16.gguf`).
    """
    cfg = config or SurvivalConfig()
    path = Path(image_path).expanduser().resolve()
    if not path.exists() or not path.is_file():
        return {
            "status": "error",
            "error": f"Fichier image introuvable : {image_path}",
        }

    raw_bytes = path.read_bytes()
    mime_type, _ = mimetypes.guess_type(str(path))
    if not mime_type:
        mime_type = "image/png" if raw_bytes.startswith(b"\x89PNG") else "image/jpeg"

    b64_preview = base64.b64encode(raw_bytes[:4096]).decode("ascii")
    triage = medical_triage_assessment(clinical_context or "plaie blessure cutanée", config=cfg)

    if cfg.medgemma_mode == "dedicated_medgemma":
        try:
            full_b64 = base64.b64encode(raw_bytes).decode("ascii")
            llama_response = _call_dedicated_medgemma_server(
                cfg.medgemma_server_url,
                full_b64,
                mime_type,
                clinical_context or "",
                cfg.http_timeout_sec,
            )
            return {
                "status": "ok",
                "execution_mode": "dedicated_medgemma_llama_server",
                "image_meta": {
                    "path": str(path),
                    "mime_type": mime_type,
                    "size_bytes": len(raw_bytes),
                },
                "medgemma_analysis": llama_response,
                "triage_context": triage,
            }
        except Exception as exc:
            fallback_reason = f"Serveur MedGemma dédié injoignable ({type(exc).__name__}) -> bascule en mode shared_vision."
        else:
            fallback_reason = None
    else:
        fallback_reason = None

    return {
        "status": "ok",
        "execution_mode": "shared_vision_gemma_4_e4b_it",
        "target_agent_model": cfg.target_agent_model,
        "fallback_notice": fallback_reason,
        "image_meta": {
            "path": str(path),
            "mime_type": mime_type,
            "size_bytes": len(raw_bytes),
            "base64_header_preview": b64_preview[:120],
        },
        "clinical_vision_prompt": MEDGEMMA_FA_VISION_RUBRIC,
        "triage_context": triage,
    }

