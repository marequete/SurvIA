"""
Domain 1: Kiwix ZIM Offline Encyclopedia Connector
Unifies and upgrades `jeffreyrampineda/kiwix-wiki-mcp-server` with:
- Full compatibility with `kiwix-serve` (HTTP OPDS catalog + HTML search/article retrieval)
- Token-efficient HTML/XML stripping tailored for Gemma 4 E4B IT on Android
- Circuit Breaker automatic fallback to the embedded SQLite/JSON Survival Library
  whenever `kiwix-serve` is not running.
"""

import html
import re
from typing import Any
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

from offline_survival_mcp.config import SurvivalConfig
from offline_survival_mcp.db.schema_and_seed import SurvivalDatabase


RECOMMENDED_ZIM_CATALOG: list[dict[str, str]] = [
    {
        "id": "wikipedia_fr_all_nopic",
        "title": "Wikipédia Hors-Ligne (Français / Anglais Sans Images - Compact Mobile)",
        "category": "encyclopedia",
        "recommended_zim": "wikipedia_fr_all_nopic.zim (~11 GB) / wikipedia_en_simple_all_nopic.zim (~1.2 GB)",
    },
    {
        "id": "mdwiki_en_all",
        "title": "WikiMed Medical Encyclopedia (Where There Is No Doctor + Offline Medicine)",
        "category": "medical",
        "recommended_zim": "mdwiki_en_all_maxi.zim (~1.8 GB)",
    },
    {
        "id": "ifixit_en_all",
        "title": "iFixit Offline Repair Manuals (Électronique, Véhicules, Solaire, Radio)",
        "category": "repair",
        "recommended_zim": "ifixit_en_all.zim (~3.4 GB)",
    },
    {
        "id": "wikibooks_survival",
        "title": "Wikibooks & StackExchange Preparedness / Outdoors / Electrical",
        "category": "survival",
        "recommended_zim": "outdoors.stackexchange.com_en_all.zim (~180 MB)",
    },
]


def strip_kiwix_html(raw_html: str, max_chars: int = 4000) -> str:
    """
    Strips scripts, styles, navigation chrome, and HTML tags from a Kiwix ZIM article
    to return clean, token-efficient plain text for Gemma 4 E4B IT.
    """
    text = re.sub(r"(?is)<(script|style|noscript|svg|header|footer|nav)\b.*?>.*?</\1>", " ", raw_html)
    text = re.sub(r"(?i)<br\s*/?>", "\n", text)
    text = re.sub(r"(?i)</(p|div|h1|h2|h3|h4|li|tr)>", "\n", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text)
    lines = [re.sub(r"[ \t]+", " ", line).strip() for line in text.splitlines()]
    compact = "\n".join(line for line in lines if line)
    if len(compact) > max_chars:
        return compact[:max_chars] + "\n...[TRONQUÉ POUR ÉCONOMIE DE CONTEXTE MOBILE GEMMA 4 E4B]..."
    return compact


def _http_get(url: str, timeout: float) -> str:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "OfflineSurvivalMCP/1.0 (Android; Gemma-4-E4B-IT)"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def kiwix_list_libraries(config: SurvivalConfig) -> dict[str, Any]:
    """
    Lists available ZIM libraries from a running `kiwix-serve` instance (`catalog/v2/entries` or `/catalog/search`),
    or lists local `.zim` files and the fallback survival catalog when `kiwix-serve` is offline.
    """
    base = config.kiwix_base_url.rstrip("/")
    local_zims = [p.name for p in config.zim_dir.glob("*.zim")]

    try:
        xml_payload = _http_get(f"{base}/catalog/entries", timeout=config.http_timeout_sec)
        root = ET.fromstring(xml_payload)
        ns = {"atom": "http://www.w3.org/2005/Atom"}
        entries: list[dict[str, str]] = []
        for entry in root.findall("atom:entry", ns):
            title = (entry.findtext("atom:title", default="", namespaces=ns) or "").strip()
            name = (entry.findtext("atom:name", default="", namespaces=ns) or "").strip()
            summary = (entry.findtext("atom:summary", default="", namespaces=ns) or "").strip()
            entries.append({"id": name or title, "title": title, "summary": summary})
        return {
            "status": "online_kiwix_serve",
            "kiwix_server": base,
            "local_zim_files": local_zims,
            "libraries": entries,
        }
    except Exception as exc:
        return {
            "status": "fallback_offline_catalog",
            "kiwix_server": base,
            "notice": (
                f"Serveur kiwix-serve non détecté sur {base} ({type(exc).__name__}). "
                "Circuit Breaker activé : utilisation du catalogue local embarqué."
            ),
            "local_zim_files": local_zims,
            "libraries": RECOMMENDED_ZIM_CATALOG,
        }


def kiwix_search(
    query: str,
    library: str | None = None,
    limit: int = 5,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Searches across offline ZIM archives via `kiwix-serve` (`/search?pattern=...`).
    Falls back to the embedded Survival FTS database if `kiwix-serve` is unreachable.
    """
    cfg = config or SurvivalConfig()
    limit = max(1, min(limit, 25))
    base = cfg.kiwix_base_url.rstrip("/")
    params: dict[str, str | int] = {"pattern": query, "pageLength": limit}
    if library:
        params["content"] = library

    search_url = f"{base}/search?{urllib.parse.urlencode(params)}"
    try:
        raw_html = _http_get(search_url, timeout=cfg.http_timeout_sec)
        matches = re.findall(
            r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', raw_html, flags=re.IGNORECASE | re.DOTALL
        )
        results: list[dict[str, str]] = []
        for href, label in matches:
            if href.startswith("/search") or href.startswith("http"):
                continue
            clean_title = strip_kiwix_html(label, max_chars=200)
            if clean_title:
                results.append({"title": clean_title, "url": href})
            if len(results) >= limit:
                break

        return {
            "mode": "kiwix_serve_live",
            "query": query,
            "library": library or "all",
            "results": results,
        }
    except Exception:
        # Circuit Breaker -> Query local survival manuals DB
        db = SurvivalDatabase(cfg)
        fallback_docs = db.search_manuals(query, limit=limit)
        return {
            "mode": "circuit_breaker_local_fts",
            "query": query,
            "library": library or "embedded_survival_db",
            "results": [
                {
                    "title": doc["title"],
                    "url": f"/local/{doc['id']}",
                    "source": doc["source"],
                    "snippet": doc["content"][:260] + "...",
                }
                for doc in fallback_docs
            ],
        }


def kiwix_get_article(
    url: str,
    max_chars: int = 4000,
    config: SurvivalConfig | None = None,
) -> dict[str, Any]:
    """
    Fetches and strips the full text of a Kiwix ZIM article or embedded fallback article.
    """
    cfg = config or SurvivalConfig()
    if url.startswith("/local/"):
        doc_id = url.replace("/local/", "").strip()
        db = SurvivalDatabase(cfg)
        store = db._load_store()
        for doc in store["manuals"]:
            if doc["id"] == doc_id:
                return {
                    "url": url,
                    "title": doc["title"],
                    "source": doc["source"],
                    "mode": "embedded_survival_db",
                    "content": doc["content"][:max_chars],
                }
        return {"url": url, "error": f"Article local '{doc_id}' introuvable."}

    base = cfg.kiwix_base_url.rstrip("/")
    rel = url if url.startswith("/") else f"/{url}"
    full_url = f"{base}{rel}"
    try:
        raw_html = _http_get(full_url, timeout=cfg.http_timeout_sec)
        return {
            "url": url,
            "mode": "kiwix_serve_live",
            "content": strip_kiwix_html(raw_html, max_chars=max_chars),
        }
    except Exception as exc:
        return {
            "url": url,
            "mode": "offline_error",
            "error": f"Impossible de récupérer {full_url} ({type(exc).__name__}). Utilisez kiwix_search pour obtenir une entrée /local/.",
        }

