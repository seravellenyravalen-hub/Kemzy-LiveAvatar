import logging
from pathlib import Path

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from pydantic import BaseModel

from .config import settings
from .deep_live_cam_runtime import DeepLiveCamRuntime
from .models import ModelRuntime
from .sessions import SessionStore
from .webrtc import webrtc_sessions

logger = logging.getLogger(__name__)
app = FastAPI(title="Kemzy Remote Live Backend", version="1.0.0")
runtime: ModelRuntime = DeepLiveCamRuntime(
    upstream_path=Path(settings.dlc_path),
    models_path=Path(settings.model_dir),
    execution_provider=settings.execution_provider,
)
sessions = SessionStore()
_runtime_error: str | None = None


class WebRTCOffer(BaseModel):
    sdp: str
    type: str


@app.on_event("startup")
def load_runtime() -> None:
    global _runtime_error
    try:
        runtime.load()
        _runtime_error = None
    except Exception as exc:
        _runtime_error = str(exc)
        logger.exception("Deep-Live-Cam runtime failed to initialize")


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "model_ready": runtime.ready,
        "inference_provider": settings.execution_provider,
        "runtime_error": _runtime_error,
    }


@app.post("/v1/sessions")
def create_session() -> dict[str, str]:
    if not runtime.ready:
        raise HTTPException(status_code=503, detail="inference runtime is not ready")
    try:
        session = sessions.create()
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return {"session_id": session.session_id, "token": session.token}


def _authorized(session_id: str, authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization required")
    token = authorization[7:].strip()
    session = sessions.get(session_id, token)
    if session is None:
        raise HTTPException(status_code=401, detail="invalid session credentials")
    return session


@app.post("/v1/sessions/{session_id}/reference")
async def upload_reference(
    session_id: str,
    file: UploadFile = File(...),
    authorization: str | None = Header(default=None),
) -> dict[str, object]:
    session = _authorized(session_id, authorization)
    if file.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=415, detail="reference must be JPEG, PNG, or WebP")
    data = await file.read()
    if not data or len(data) > 10 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="reference image is empty or too large")
    try:
        runtime.set_reference(data)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    session.reference = data
    return {"status": "ok", "model_ready": runtime.ready}


@app.post("/v1/sessions/{session_id}/offer")
async def accept_offer(
    session_id: str,
    offer: WebRTCOffer,
    authorization: str | None = Header(default=None),
) -> dict[str, str]:
    session = _authorized(session_id, authorization)
    if session.reference is None:
        raise HTTPException(status_code=409, detail="reference image is required")
    if not runtime.ready:
        raise HTTPException(status_code=503, detail="inference runtime is not ready")
    try:
        sdp, sdp_type = await webrtc_sessions.accept_offer(
            session_id=session_id,
            sdp=offer.sdp,
            sdp_type=offer.type,
            runtime=runtime,
        )
    except Exception as exc:
        await webrtc_sessions.close(session_id)
        raise HTTPException(status_code=502, detail=f"WebRTC negotiation failed: {exc}") from exc
    return {"sdp": sdp, "type": sdp_type}


@app.delete("/v1/sessions/{session_id}")
async def delete_session(session_id: str, authorization: str | None = Header(default=None)) -> dict[str, str]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization required")
    if not sessions.delete(session_id, authorization[7:].strip()):
        raise HTTPException(status_code=401, detail="invalid session credentials")
    await webrtc_sessions.close(session_id)
    return {"status": "stopped"}
