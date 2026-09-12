# Live Portrait Animator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current INSwapper live path with a DeepFaceLive/LIA-style portrait animation path in Kémzy, where an uploaded portrait is the visual source and live camera motion drives continuous animation.

**Architecture:** Keep the #286 foundation, branding, passcode, model storage, and disk-backed model loading. Introduce a portrait-animation abstraction with a LIA-compatible ONNX implementation: prepare the uploaded source once, extract driver motion from the newest camera frame, generate a 256x256 animated result, and render only the freshest frame through a bounded low-latency pipeline. Do not load large models with `readBytes()`.

**Tech Stack:** Kotlin, Android CameraX, ML Kit face detection, ONNX Runtime, existing Kémzy model repository and CI.

**Spec:** DeepFaceLive Face Animator/LIA behavior: static animatable image + live driver motion + continuous generated output.

## Global Constraints

- Preserve Kémzy #286 branding, passcode-only unlock, and existing model-storage behavior.
- Do not redownload the existing 696 MB INSwapper models.
- Do not claim an APK is ready until CI succeeds and the exact artifact checksum is verified.
- Do not ask the user to download the LIA model until the Android implementation can import/validate it.
- Avoid Java heap copies of large model files.
- Prefer newest-frame processing and drop stale frames to minimize latency.
- The uploaded portrait is the visual source; the camera supplies motion/expression/pose information.
- A single 2D portrait cannot reproduce arbitrary unseen 3D views perfectly; output quality must be described accurately.

## File Map

- `android/app/src/main/java/com/kemzy/liveavatar/InferenceEngine.kt` — ONNX tensor/session support and LIA inference primitives.
- `android/app/src/main/java/com/kemzy/liveavatar/LiveSwapPipeline.kt` — camera analysis, motion extraction, bounded frame scheduling, and preview rendering.
- `android/app/src/main/java/com/kemzy/liveavatar/MainActivity.kt` — source-photo selection, model readiness, controls, and lifecycle integration.
- `android/app/src/main/java/com/kemzy/liveavatar/ModelRepository.kt` — persistent import/validation of LIA model files.
- `android/app/src/test/java/...` — preprocessing, tensor-shape, model-loading, and scheduling regression tests.
- `tools/` — only offline model assembly/validation helpers; no user model download is committed.

## Tasks

- [ ] Inspect the current #286-derived live pipeline and identify the smallest seam for `PortraitAnimator`.
- [ ] Write failing tests for LIA preprocessing, 256x256 NCHW conversion, source caching, output conversion, and model-path loading.
- [ ] Implement the LIA model repository/import contract, including support for split generator files without heap concatenation.
- [ ] Implement `PortraitAnimator` and LIA-compatible ONNX inference using disk-backed sessions.
- [ ] Implement motion extraction and generation using the newest available camera frame only.
- [ ] Replace the current live preview path with the portrait-animation path while preserving existing app navigation and passcode behavior.
- [ ] Add model readiness/error UI that clearly distinguishes missing LIA model from runtime failure.
- [ ] Run unit tests and Android build locally/through CI; fix failures before producing an artifact.
- [ ] Verify the exact CI commit, run number, artifact name, size, and SHA-256 before asking the user to install anything.
- [ ] Only after the build is verified, guide the user through the one required LIA model download in Termux and its exact placement/import commands.
- [ ] After model installation, validate live performance on the Infinix Smart 10 and tune frame dropping/resolution/acceleration based on measured behavior rather than promising a fixed FPS.
