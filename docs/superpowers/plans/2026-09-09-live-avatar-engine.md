# Live Avatar Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the tracking-only avatar preview with an on-device face-swap engine foundation that can load a user-selected source face, process camera frames, and expose accelerator/model readiness without claiming a swap until the neural pipeline is actually available.

**Architecture:** Keep CameraX and ML Kit responsible for camera delivery and fast target-face tracking. Add a replaceable ONNX Runtime engine boundary with model manifest/download state, source-face preparation, and a frame-processing API; use CPU/XNNPACK first and NNAPI when supported. Keep the existing overlay as an explicit fallback until all required models are present.

**Tech Stack:** Kotlin, Android API 26+, CameraX 1.6.2, ML Kit face detection 16.1.7, ONNX Runtime Android, JUnit 4.

**Spec:** `docs/superpowers/specs/live-avatar-android.md`

## Global Constraints

- Core camera/AI processing is local; no user image upload is required.
- Do not bundle or redistribute restricted InsightFace/FaceFusion model weights; model licensing must be respected.
- Do not claim neural face replacement when the runtime is operating in tracking fallback mode.
- Preserve the uploaded source face identity as faithfully as the selected model permits; do not intentionally beautify or redesign it.
- Keep CPU fallback available when hardware acceleration is unavailable.
- Use TDD for behavioral code and verify with GitHub Actions before claiming success.

---

### Task 1: Define the neural engine contract and runtime state

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/LiveFaceEngineStateTest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/LiveFaceEngineState.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/FaceSwapEngine.kt`

**Interfaces:**
- Produces `LiveFaceEngineState` with `Idle`, `Preparing`, `Ready`, and `Fallback(reason)` states.
- Produces `FaceSwapFrame` carrying optional processed bitmap plus whether the result is neural or fallback.

- [ ] **Step 1: Write failing tests** for state transitions and frame labeling.
- [ ] **Step 2: Run `gradle testDebugUnitTest` and confirm the new tests fail because the types do not exist.**
- [ ] **Step 3: Implement the state/frame data types and extend the engine interface with `prepareAvatar`, `processFrame`, and `state`.
- [ ] **Step 4: Run the unit tests and confirm they pass.**
- [ ] **Step 5: Commit `feat: define live face engine contract`.**

### Task 2: Add ONNX Runtime and accelerator selection

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/InferenceBackendPolicyTest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/InferenceBackend.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`

**Interfaces:**
- `InferenceBackend` exposes `CPU`, `XNNPACK`, and `NNAPI` choices.
- `InferenceBackendSelector.select(apiLevel: Int, nnapiAvailable: Boolean)` returns the preferred backend with CPU fallback.

- [ ] **Step 1: Write failing policy tests.**
- [ ] **Step 2: Run the targeted unit tests and confirm failure.**
- [ ] **Step 3: Add `com.microsoft.onnxruntime:onnxruntime-android` and implement backend policy; keep CPU as guaranteed fallback.**
- [ ] **Step 4: Add the required R8 keep rule for `ai.onnxruntime`.**
- [ ] **Step 5: Run unit tests and commit `feat: add on-device inference runtime`.**

### Task 3: Add model inventory and local model storage

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/ModelManifestTest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/ModelManifest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/ModelStore.kt`

**Interfaces:**
- Model inventory identifies detector, ArcFace embedder, swapper, and optional expression-restoration models.
- `ModelStore` reports whether a model file exists and its byte size without downloading anything automatically.

- [ ] **Step 1: Write failing tests for required model inventory and missing/present states.**
- [ ] **Step 2: Run targeted tests and confirm failure.**
- [ ] **Step 3: Implement immutable model descriptors and app-private storage lookup.**
- [ ] **Step 4: Run tests and commit `feat: add local face model inventory`.**

### Task 4: Prepare the selected avatar for identity embedding

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/AvatarPreparationTest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/AvatarPreparation.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/FaceTracker.kt`

**Interfaces:**
- `AvatarPreparation.prepare(uri)` returns a validation result indicating whether a usable source face was found and why it failed otherwise.
- Target tracking exposes enough landmark/expression data for the later swap stage without changing the existing CameraX lifecycle.

- [ ] **Step 1: Write failing tests for valid source, no-source-face, and invalid input.**
- [ ] **Step 2: Run targeted tests and confirm failure.**
- [ ] **Step 3: Implement source validation and enable ML Kit landmarks/classification needed for blink/smile/eye-open signals while retaining pose tracking.**
- [ ] **Step 4: Run tests and commit `feat: prepare source face and expression tracking`.**

### Task 5: Implement ONNX session loading and readiness checks

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/OnDeviceFaceSwapEngineTest.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/OnDeviceFaceSwapEngine.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/OnnxSessionFactory.kt`

**Interfaces:**
- Engine remains `Fallback` when required models are missing.
- Engine becomes `Ready` only when detector, embedder, and swapper sessions can be created.
- Session factory selects XNNPACK/NNAPI/CPU according to the backend policy and never requires a network connection.

- [ ] **Step 1: Write failing tests for missing-model fallback and readiness rules using a fake session factory.**
- [ ] **Step 2: Run targeted tests and confirm failure.**
- [ ] **Step 3: Implement session factory and engine lifecycle; do not fake successful neural output.**
- [ ] **Step 4: Run tests and commit `feat: add on-device face swap engine lifecycle`.**

### Task 6: Connect the real engine boundary to the camera preview

**Files:**
- Test: `app/src/test/java/com/kemzy/liveavatar/LiveFramePipelineTest.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/MainActivity.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/FaceSwapEngine.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/AvatarOverlayModel.kt`

- [ ] **Step 1: Write failing pipeline tests for neural-ready versus fallback frames.**
- [ ] **Step 2: Run tests and confirm failure.**
- [ ] **Step 3: Wire camera frames into the engine boundary and display the neural frame only when the engine returns one; otherwise retain the clearly labeled tracking fallback.**
- [ ] **Step 4: Add status text showing `AI face swap ready`, `Preparing AI models`, or `Tracking fallback`.**
- [ ] **Step 5: Run tests and commit `feat: connect live face engine to camera`.**

### Task 7: Verify build, CI, and device diagnostics

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `app/src/main/java/com/kemzy/liveavatar/DeviceDiagnostics.kt`

- [ ] **Step 1: Add tests for deterministic diagnostics formatting.**
- [ ] **Step 2: Run the unit tests.**
- [ ] **Step 3: Ensure CI builds the debug APK and uploads it.**
- [ ] **Step 4: Inspect the latest workflow job and artifact; fix any failures.**
- [ ] **Step 5: Commit `test: verify live avatar Android foundation`.**

**Device validation after CI:** install the debug APK on the user's phone, select a clear face image, start the front camera, confirm tracking and engine state, then capture the device SOC/ABI diagnostics before choosing a device-specific accelerator or model package.
