---
title: Kemzy-LiveAvatar Deep-Live-Cam
emoji: 🎭
colorFrom: indigo
colorTo: purple
sdk: gradio
sdk_version: 5.44.1
app_file: app.py
pinned: false
---

# Kemzy-LiveAvatar Deep-Live-Cam backend

This Space provides the remote inference service used by Kemzy-LiveAvatar.

It uses the Deep-Live-Cam-compatible InsightFace face analysis pipeline and the FP16 INSwapper model from `hacksider/deep-live-cam`. It intentionally omits face enhancement models to keep the runtime smaller.

Use only with images/faces you own or have permission to process. Do not use this service for fraud, identity verification bypass, or impersonation.

## Kemzy API

- `prepare_avatar`: upload one source face and receive a temporary in-memory session id.
- `process_frame`: send a session id plus a target frame and receive the processed frame.

Every Gradio Space exposes its API through `/gradio_api/info` and the queue-based `/gradio_api/call/<endpoint>` endpoints.
