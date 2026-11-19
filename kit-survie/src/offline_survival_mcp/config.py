from dataclasses import dataclass, field
import os
from pathlib import Path
import platform


def _detect_android() -> bool:
    """Detects whether the MCP server is running on Android / Termux."""
    if "ANDROID_ROOT" in os.environ or "TERMUX_VERSION" in os.environ:
        return True
    prefix = os.environ.get("PREFIX", "")
    return "com.termux" in prefix or "android" in platform.platform().lower()


@dataclass
class SurvivalConfig:
    """Global configuration for the Offline Survival MCP Server."""

    root_dir: Path = field(default_factory=lambda: Path(__file__).resolve().parents[2])
    data_dir: Path | None = None

    # Local loopback endpoints (100% air-gapped 127.0.0.1)
    kiwix_base_url: str = field(
        default_factory=lambda: os.environ.get("KIWIX_SERVER_URL", "http://127.0.0.1:8081")
    )
    medgemma_server_url: str = field(
        default_factory=lambda: os.environ.get("MEDGEMMA_SERVER_URL", "http://127.0.0.1:8083")
    )
    pmtiles_port: int = field(
        default_factory=lambda: int(os.environ.get("PMTILES_PORT", "8082"))
    )
    mcp_http_port: int = field(
        default_factory=lambda: int(os.environ.get("MCP_HTTP_PORT", "8000"))
    )

    # Target LLM & Mobile RAM strategy:
    # - "shared_vision": Recommended on Android with Gemma 4 E4B IT (multimodal).
    #   Uses Gemma 4 E4B IT's native vision + MedGemma clinical prompts without loading a 2nd 4B model in RAM.
    # - "dedicated_medgemma": Queries a separate llama.cpp server running MedGemma-4B-IT + mmproj.
    target_agent_model: str = field(
        default_factory=lambda: os.environ.get("TARGET_AGENT_MODEL", "gemma-4-e4b-it")
    )
    medgemma_mode: str = field(
        default_factory=lambda: os.environ.get("MEDGEMMA_MODE", "shared_vision")
    )

    is_android: bool = field(default_factory=_detect_android)
    http_timeout_sec: float = 2.5

    def __post_init__(self) -> None:
        if self.data_dir is None:
            env_data = os.environ.get("SURVIVAL_DATA_DIR")
            self.data_dir = Path(env_data) if env_data else (self.root_dir / "data")
        else:
            self.data_dir = Path(self.data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)

    @property
    def sqlite_db_path(self) -> Path:
        return self.data_dir / "survival_knowledge.db"

    @property
    def json_store_path(self) -> Path:
        return self.data_dir / "survival_knowledge_store.json"

    @property
    def pmtiles_dir(self) -> Path:
        path = self.data_dir / "maps"
        path.mkdir(parents=True, exist_ok=True)
        return path

    @property
    def zim_dir(self) -> Path:
        path = self.data_dir / "zim"
        path.mkdir(parents=True, exist_ok=True)
        return path

