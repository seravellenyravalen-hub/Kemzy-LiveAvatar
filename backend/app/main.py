from fastapi import FastAPI

from .models import ModelRuntime, UnconfiguredModelRuntime

app = FastAPI(title="Kemzy Remote Live Backend", version="1.0.0")
runtime: ModelRuntime = UnconfiguredModelRuntime()


@app.on_event("startup")
def load_runtime() -> None:
    runtime.load()


@app.get("/health")
def health() -> dict[str, object]:
    return {"status": "ok", "model_ready": runtime.ready}
