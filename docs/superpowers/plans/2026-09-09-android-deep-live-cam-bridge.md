# Android Deep-Live-Cam Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Android 15+ implementation layer that exposes the existing Deep-Live-Cam workflow on Android without modifying any existing upstream Deep-Live-Cam file.

**Architecture:** A self-contained Gradle project lives only under `android/`. Native Android/CameraX handles lifecycle, camera preview, capture, recording, and UI; bounded pipeline interfaces isolate real ONNX inference and model handling. The existing Python desktop tree remains the desktop implementation and is never edited.

**Tech Stack:** Kotlin, Android Gradle Plugin, Android 15/API 35 minimum, CameraX, Jetpack lifecycle/activity, Media3 where needed for playback, ONNX Runtime Android, Android Keystore-backed local state, JUnit/Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-09-android-deep-live-cam-bridge-design.md`

## Global Constraints

- Do not edit, delete, rename, or reformat any existing Deep-Live-Cam file.
- All Android-specific code lives in newly added paths under `android/` plus implementation documentation.
- Existing upstream Python behavior remains available for desktop users.
- Android minimum supported OS is Android 15 / API 35.
- The Android layer must use real model execution when compatible models are installed; never fake a live result.
- Existing upstream ONNX weights are not assumed to be present in Git; Android model handling must be separate.
- Unsupported desktop-only capabilities must be reported honestly rather than exposed as nonfunctional controls.
- The app must use explicit responsible-use/consent messaging.

---

### Task 1: Android project scaffold and repository boundary guard

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/.../KemzyLiveAvatarApplication.kt`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Create: `android/README.md`
- Create: `android/src/test/.../RepositoryBoundaryTest.kt`

**Interfaces:**
- Produces a buildable Android application with `minSdk = 35`.
- Produces a repository-boundary test that enumerates tracked files outside `android/` and the new docs path and fails if an implementation commit changes them.

- [ ] **Step 1: Write the failing build/boundary tests**
- [ ] **Step 2: Run the tests/build and verify failure is caused by the missing Android project**
- [ ] **Step 3: Add the minimal Gradle project and manifest**
- [ ] **Step 4: Run `./gradlew :app:test` and `./gradlew :app:assembleDebug` and verify green**
- [ ] **Step 5: Verify `git diff --name-only` contains only new Android/docs paths**
- [ ] **Step 6: Commit `feat: scaffold Android 15 bridge`**

### Task 2: Core pipeline contracts and bounded frame processing

**Files:**
- Create: `android/app/src/main/java/.../pipeline/Frame.kt`
- Create: `android/app/src/main/java/.../pipeline/FramePipeline.kt`
- Create: `android/app/src/main/java/.../pipeline/PipelineState.kt`
- Create: `android/app/src/test/java/.../pipeline/FramePipelineTest.kt`

**Interfaces:**
- `FramePipeline.submit(frame: Frame): Boolean`
- `FramePipeline.start(processor: suspend (Frame) -> Unit)`
- `FramePipeline.stop()`
- `FramePipeline.pendingCount(): Int`

- [ ] **Step 1: Write a failing test proving the queue is bounded and stale frames are dropped**
- [ ] **Step 2: Run the focused test and verify the expected failure**
- [ ] **Step 3: Implement a single-slot/latest-frame backpressure policy**
- [ ] **Step 4: Run focused tests and verify green**
- [ ] **Step 5: Commit `feat: add bounded Android frame pipeline`**

### Task 3: Source image repository and model repository

**Files:**
- Create: `android/app/src/main/java/.../models/SourceFaceRepository.kt`
- Create: `android/app/src/main/java/.../models/ModelRepository.kt`
- Create: `android/app/src/main/java/.../models/ModelStatus.kt`
- Create: `android/app/src/test/java/.../models/SourceFaceRepositoryTest.kt`
- Create: `android/app/src/test/java/.../models/ModelRepositoryTest.kt`

**Interfaces:**
- `SourceFaceRepository.import(uri): SourceFace`
- `SourceFaceRepository.current(): SourceFace?`
- `SourceFaceRepository.clear()`
- `ModelRepository.status(): ModelStatus`
- `ModelRepository.importModel(uri, expectedName): ModelStatus`
- `ModelRepository.openModel(name): InputStream`

- [ ] **Step 1: Write tests proving source bytes remain unchanged and invalid model files are rejected**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement app-private source/model storage outside the upstream tree**
- [ ] **Step 4: Run tests and verify GREEN**
- [ ] **Step 5: Commit `feat: add source and model repositories`**

### Task 4: Real ONNX inference adapter and capability detection

**Files:**
- Create: `android/app/src/main/java/.../inference/FaceDetectionEngine.kt`
- Create: `android/app/src/main/java/.../inference/FaceSwapEngine.kt`
- Create: `android/app/src/main/java/.../inference/FaceEnhancementEngine.kt`
- Create: `android/app/src/main/java/.../inference/OnnxRuntimeProvider.kt`
- Create: `android/app/src/main/java/.../inference/OnnxFaceSwapEngine.kt`
- Create: `android/app/src/test/java/.../inference/OnnxRuntimeProviderTest.kt`

**Interfaces:**
- `FaceDetectionEngine.detect(frame): List<DetectedFace>`
- `FaceSwapEngine.swap(frame, source, faces, options): ProcessedFrame`
- `FaceEnhancementEngine.enhance(frame): ProcessedFrame`
- `OnnxRuntimeProvider.createSession(model, acceleration): SessionHandle`

- [ ] **Step 1: Write failing contract tests for missing/incompatible models and provider fallback**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement ONNX Runtime session creation and explicit capability errors**
- [ ] **Step 4: Add the real face-swap adapter only for validated compatible ONNX graphs; do not synthesize output**
- [ ] **Step 5: Run tests and verify GREEN**
- [ ] **Step 6: Commit `feat: add Android ONNX inference adapter`**

### Task 5: CameraX live camera and lifecycle-safe rendering

**Files:**
- Create: `android/app/src/main/java/.../camera/CameraInput.kt`
- Create: `android/app/src/main/java/.../camera/CameraController.kt`
- Create: `android/app/src/main/java/.../live/LiveSession.kt`
- Create: `android/app/src/main/java/.../live/LiveRenderer.kt`
- Create: `android/app/src/test/java/.../live/LiveSessionTest.kt`

**Interfaces:**
- `CameraInput.start(previewView, analyzer)`
- `CameraInput.stop()`
- `LiveSession.start()`
- `LiveSession.stop()`
- `LiveSession.reset()`

- [ ] **Step 1: Write failing lifecycle tests for start → stop → reopen and stale-source reset**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement CameraX Preview plus ImageAnalysis with bounded analysis**
- [ ] **Step 4: Connect processed frames to the renderer without blocking camera callbacks**
- [ ] **Step 5: Run tests and an Android instrumented camera lifecycle test where hardware is available**
- [ ] **Step 6: Commit `feat: add lifecycle-safe CameraX live pipeline`**

### Task 6: Recording, import, processing, and playback

**Files:**
- Create: `android/app/src/main/java/.../media/OutputRecorder.kt`
- Create: `android/app/src/main/java/.../media/VideoProcessor.kt`
- Create: `android/app/src/main/java/.../media/PlaybackRepository.kt`
- Create: `android/app/src/test/java/.../media/OutputRecorderTest.kt`
- Create: `android/app/src/test/java/.../media/PlaybackRepositoryTest.kt`

**Interfaces:**
- `OutputRecorder.start(config)`
- `OutputRecorder.write(frame)`
- `OutputRecorder.stop(): Uri`
- `VideoProcessor.process(input, options): Uri`
- `PlaybackRepository.open(uri)`

- [ ] **Step 1: Write failing tests for recording state and playback artifact creation**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement bounded recording and explicit failure states**
- [ ] **Step 4: Implement imported-video processing through the same real inference interface**
- [ ] **Step 5: Run tests and verify GREEN**
- [ ] **Step 6: Commit `feat: add recording and media processing`**

### Task 7: Deep-Live-Cam feature controls and performance controller

**Files:**
- Create: `android/app/src/main/java/.../features/LiveOptions.kt`
- Create: `android/app/src/main/java/.../features/PerformanceController.kt`
- Create: `android/app/src/main/java/.../features/CapabilityMatrix.kt`
- Create: `android/app/src/test/java/.../features/PerformanceControllerTest.kt`

**Interfaces:**
- `LiveOptions` includes mirror, resizable preview, target FPS, processing resolution, many-faces, face mapping, mouth-mask, and enhancement flags.
- `PerformanceController.chooseProvider()`
- `PerformanceController.recommendedFrameBudget()`
- `CapabilityMatrix.supports(feature): Capability`

- [ ] **Step 1: Write failing tests for bounded FPS/resolution selection and honest unsupported-feature reporting**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement adaptive performance settings and provider fallback**
- [ ] **Step 4: Implement capability reporting so unsupported operations are disabled/explained**
- [ ] **Step 5: Run tests and verify GREEN**
- [ ] **Step 6: Commit `feat: add mobile performance and feature controls`**

### Task 8: Privacy lock, permissions, settings, and main UI

**Files:**
- Create: `android/app/src/main/java/.../security/PrivacyLock.kt`
- Create: `android/app/src/main/java/.../ui/LockActivity.kt`
- Create: `android/app/src/main/java/.../ui/MainActivity.kt`
- Create: `android/app/src/main/java/.../ui/SettingsActivity.kt`
- Create: `android/app/src/main/java/.../ui/LiveScreen.kt`
- Create: `android/app/src/main/res/layout/activity_lock.xml`
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Create: `android/app/src/main/res/layout/activity_settings.xml`
- Create: `android/app/src/test/java/.../security/PrivacyLockTest.kt`

**Interfaces:**
- `PrivacyLock.isLocked()`
- `PrivacyLock.unlock(passcode)`
- `PrivacyLock.lock()`
- `PrivacyLock.onBackground()`

- [ ] **Step 1: Write failing tests for lock/re-entry behavior and permission states**
- [ ] **Step 2: Run tests and verify RED**
- [ ] **Step 3: Implement privacy lock and optional biometric gate after the primary passcode**
- [ ] **Step 4: Implement source selection, compact thumbnail, real PreviewView, Live/Record/Stop/Import/Playback controls**
- [ ] **Step 5: Implement settings/help for camera permission, model state, acceleration, FPS/resolution, diagnostics, and unsupported features**
- [ ] **Step 6: Run tests and verify GREEN**
- [ ] **Step 7: Commit `feat: add Kemzy-LiveAvatar Android UI`**

### Task 9: End-to-end validation and upstream immutability proof

**Files:**
- Create: `android/app/src/androidTest/java/.../KemzyLiveAvatarSmokeTest.kt`
- Create: `android/tools/verify-upstream-unchanged.sh`
- Modify: none outside newly created `android/` and implementation documentation paths.

**Interfaces:**
- Smoke test verifies launch, lock, camera permission flow, source import, model-status flow, live session start/stop, recording state, and playback navigation without claiming inference success when models are absent.

- [ ] **Step 1: Write the failing smoke/boundary verification**
- [ ] **Step 2: Run it and verify RED for missing app functionality**
- [ ] **Step 3: Wire all implemented components into the smoke flow**
- [ ] **Step 4: Run unit tests, instrumented tests where available, and debug APK build**
- [ ] **Step 5: Compare the final tree against the pre-implementation upstream commit and prove no existing Deep-Live-Cam path was edited**
- [ ] **Step 6: Commit `test: verify Android bridge and upstream immutability`**
- [ ] **Step 7: Report exact added paths, build result, test result, and any hardware/model limitations without overstating support**

## Self-review checklist

- Spec coverage: architecture, product scope, desktop compatibility, performance, UI flow, model handling, safety, testing, and all acceptance criteria are covered by Tasks 1–9.
- No existing upstream file is listed as a modification target.
- Model weights are handled separately; the plan does not claim they are already in Git.
- Real inference is required; fake output is explicitly prohibited.
- Android 15+ is enforced at Gradle configuration level.
- Recording/import/playback and lifecycle behavior have dedicated tests.
- The final task proves repository immutability before claiming completion.
