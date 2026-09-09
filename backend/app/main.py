from fastapi import FastAPI, File, Header, HTTPException, UploadFile

from .models import ModelRuntime, UnconfiguredModelRuntime
from .sessions import SessionStore

app = FastAPI(title="Kemzy Remote Live Backend", version="1.0.0")
runtime: ModelRuntime = UnconfiguredModelRuntime()
sessions = SessionStore()


@app.on_event("startup")
def load_runtime() -> None:
    runtime.load()


@app.get("/health")
def health() -> dict[str, object]:
    return {"status": "ok", "model_ready": runtime.ready}


@app.post("/v1/sessions")
def create_session() -> dict[str, str]:
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
    session.reference = data
    runtime.set_reference(data)
    return {"status": "ok", "model_ready": runtime.ready}


@app.delete("/v1/sessions/{session_id}")
def delete_session(session_id: str, authorization: str | None = Header(default=None)) -> dict[str, str]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization required")
    if not sessions.delete(session_id, authorization[7:].strip()):
        raise HTTPException(status_code=401, detail="invalid session credentials")
    return {"status": "stopped"}
