# Kemzy Remote Live Backend

This service is the Internet-facing GPU side of Kemzy LiveAvatar. Android captures the camera; this service owns the face-swap models and returns processed video over WebRTC.

## Runtime boundary

The Android APK never downloads or runs the Deep-Live-Cam/InsightFace face-swap models. Model weights stay on the GPU service and are supplied through deployment-time storage or download configuration.

The current skeleton intentionally reports `model_ready: false` until the real Deep-Live-Cam adapter is installed. It never substitutes a fake image transform and never reports a processed stream as ready without a working model.

## Local smoke test

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
pytest tests/test_health.py -v
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

Then request `GET /health`.

## Production

Deploy the Docker image on a persistent GPU-capable host with HTTPS/WSS/WebRTC support and a stable public hostname. Set `KEMZY_MODEL_DIR`, `KEMZY_SESSION_TTL_SECONDS`, and `KEMZY_MAX_SESSIONS` at deployment time.

Do not put model weights in Git or inside the Android APK.
