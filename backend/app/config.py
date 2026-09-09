from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    model_dir: str = os.getenv("KEMZY_MODEL_DIR", "/models")
    session_ttl_seconds: int = int(os.getenv("KEMZY_SESSION_TTL_SECONDS", "3600"))
    max_sessions: int = int(os.getenv("KEMZY_MAX_SESSIONS", "4"))
    require_gpu: bool = os.getenv("KEMZY_REQUIRE_GPU", "true").lower() == "true"


settings = Settings()
