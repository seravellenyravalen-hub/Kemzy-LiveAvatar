# Live Avatar Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Android APK that uses the phone's own camera to render a selected face/avatar as a real-time local live preview.

**Architecture:** Kotlin Android app with CameraX, a dedicated face-tracking interface, a replaceable mobile AI face engine, and a real-time renderer. The AI engine is selected by measured performance on the target device rather than hard-coded to DeepFaceLab.

**Tech Stack:** Kotlin, Android SDK, Gradle, CameraX, Android graphics/rendering APIs, mobile ML runtime selected during benchmarking.

**Spec:** `docs/superpowers/specs/live-avatar-android.md`

## Global Constraints
- Android-only APK.
- Camera is owned by this APK.
- Core experience is local on-device processing where practical.
- Start/Stop explicitly controls the live session.
- AI engine must be replaceable behind an interface.
- Performance is determined by testing on the target phone.
- No third-party app camera injection is part of the core scope.
- Follow TDD for behavioral production code.

---

### Task 1: Android project foundation

**Files:**
- Create: standard Gradle Android project structure.
- Create: `app/src/main/java/.../MainActivity.kt`.
- Create: `app/src/main/AndroidManifest.xml`.
- Create: `app/src/test/...` for session-state tests.

**Interfaces:**
- Produces an installable debug APK shell.
- Produces a session state model used by camera and UI layers.

- [ ] Create the project and verify Gradle configuration.
- [ ] Write a failing test for `Idle -> Running -> Idle` session transitions.
- [ ] Run the test and verify the expected failure.
- [ ] Implement the minimal session state model.
- [ ] Run the test and verify it passes.
- [ ] Build the debug APK.
- [ ] Verify the APK installs and launches on the test device.

### Task 2: CameraX live preview

**Files:**
- Create/modify: camera controller and preview composable/view.
- Modify: `AndroidManifest.xml` for camera permission.
- Create: camera lifecycle tests where practical.

**Interfaces:**
- Consumes the session state from Task 1.
- Produces camera frames for the tracking pipeline.

- [ ] Write tests for camera-session lifecycle decisions.
- [ ] Verify tests fail before implementation.
- [ ] Implement CameraX permission and lifecycle handling.
- [ ] Run tests.
- [ ] Install APK and verify front-camera preview manually.
- [ ] Verify Stop releases the camera.

### Task 3: Face tracking abstraction

**Files:**
- Create: `FaceTracker` interface.
- Create: tracking result model for landmarks/head pose.
- Create: Android tracker implementation.
- Create: tracker unit tests.

**Interfaces:**
- Input: camera frame.
- Output: timestamped face tracking result with detection confidence, landmarks and head pose.

- [ ] Write failing tests for stable tracking-result validation.
- [ ] Verify failure.
- [ ] Implement minimal tracking models and validation.
- [ ] Verify tests pass.
- [ ] Integrate a suitable on-device face tracker.
- [ ] Test detection and head movement on-device.
- [ ] Record FPS/latency baseline.

### Task 4: Avatar selection and preparation

**Files:**
- Create: gallery picker flow.
- Create: avatar data model.
- Create: avatar preparation interface and implementation.
- Create: unit tests for valid/invalid image handling.

**Interfaces:**
- Input: local image URI.
- Output: prepared local avatar representation consumed by the AI engine.

- [ ] Write failing image-validation tests.
- [ ] Verify failure.
- [ ] Implement validation and local avatar storage.
- [ ] Verify tests pass.
- [ ] Add gallery selection UI.
- [ ] Test with several portrait images.

### Task 5: Mobile AI engine benchmark

**Files:**
- Create: `FaceEngine` interface.
- Create: benchmark harness and result model.
- Add model/runtime integration only after benchmark candidates are identified.

**Interfaces:**
- Input: camera frame + prepared avatar + tracking result.
- Output: transformed frame or renderable face layer plus timing/error metadata.

- [ ] Define engine contract and write failing contract tests.
- [ ] Verify failure.
- [ ] Implement a fake engine for tests.
- [ ] Verify tests pass.
- [ ] Evaluate suitable mobile model/runtime candidates.
- [ ] Benchmark CPU/GPU/NPU where available.
- [ ] Choose the best practical engine for the target device.

### Task 6: Real-time renderer and live avatar

**Files:**
- Create: renderer abstraction.
- Create: real-time frame pipeline.
- Modify: live camera screen.
- Create: pipeline tests using fake tracker/engine.

**Interfaces:**
- `CameraFrame -> FaceTracker -> FaceEngine -> Renderer`.

- [ ] Write failing pipeline tests with fake components.
- [ ] Verify failure.
- [ ] Implement minimal pipeline orchestration.
- [ ] Verify tests pass.
- [ ] Connect the real tracker and selected engine.
- [ ] Render the transformed result in the APK.
- [ ] Test head movement, expressions, lighting and different images.

### Task 7: Performance and lifecycle hardening

**Files:**
- Modify: pipeline, renderer and lifecycle components as required.
- Create: performance telemetry model/tests.

- [ ] Add measurements for FPS, frame latency and memory.
- [ ] Test sustained sessions.
- [ ] Optimize bottlenecks identified by measurements.
- [ ] Verify camera/model resources are released on Stop/background/error.
- [ ] Re-run automated tests and device tests.

### Task 8: Optional voice subsystem

**Files:**
- Create: microphone/audio controller.
- Create: audio session state/tests.

- [ ] Write failing audio lifecycle tests.
- [ ] Implement minimal microphone lifecycle.
- [ ] Verify tests pass.
- [ ] Add optional user's own voice input/processing.
- [ ] Verify starting/stopping the avatar session controls audio correctly.

### Task 9: Release APK verification

**Files:**
- Create/modify: release build configuration and documentation.
- Create: test checklist.

- [ ] Build release candidate APK.
- [ ] Install it on the target phone.
- [ ] Test permissions, camera, avatar selection, live transformation and Stop.
- [ ] Test low-light and high-motion conditions.
- [ ] Record known limitations rather than claiming unsupported performance.
- [ ] Run the complete automated test suite.
