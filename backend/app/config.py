from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    dlc_path: str = os.getenv("KEMZY_DLC_PATH", "/opt/Deep-Live-Cam")
    model_dir: str = os.getenv("KEMZY_MODELS_PATH", "/opt/Deep-Live-Cam/models")
    execution_provider: str = os.getenv("KEMZY_EXECUTION_PROVIDER", "CUDAExecutionProvider")
    session_ttl_seconds: int = int(os.getenv("KEMZY_SESSION_TTL_SECONDS", "3600"))
    max_sessions: int = int(os.getenv("KEMZY_MAX_SESSIONS", "4"))
    require_gpu: bool = os.getenv("KEMZY_REQUIRE_GPU", "true").lower() == "true"


settings = Settings()
