# Kémzy Avatar Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old Android face-swap runtime with a persistent LivePortrait Studio foundation and produce a verified `Kémzyavatarstudio.apk`.

**Architecture:** CameraX feeds a bounded latest-frame pipeline. A LivePortrait engine owns file-backed ONNX sessions and processes the selected persistent source portrait using driver motion and retargeting. Independent output sinks provide preview, RTMP, and a MyCam bridge; persistent Studio state controls source, model bundle, voice, camera and outputs.

**Tech Stack:** Kotlin, Android SDK 35/36, CameraX 1.6.2, ONNX Runtime Android 1.22.0, ML Kit where useful for fallback tracking, RootEncoder 2.7.2, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-12-kemzy-avatar-studio-design.md`

## Global Constraints

- Display name: `Kémzy àvátâr`.
- Final APK filename: `Kémzyavatarstudio.apk`.
- Application ID: `com.kemzy.liveavatar`.
- Passcode-only unlock remains `115522`.
- Existing launcher icon remains unchanged.
- Do not delete the user's existing `inswapper_128.onnx`.
- LivePortrait models stay outside Git and are imported once into private app storage.
- Never load large ONNX files with `readBytes()`.
- Live mode must fail closed when required models or runtime interfaces are invalid.

---

### Task 1: Stabilize model contract and file-backed ONNX runtime
**Files:** `LivePortraitModelSpec.kt`, `OnnxInferenceEngine.kt`, `LivePortraitModelValidator.kt`, related tests.

- [ ] Make `warping_spade-fix.onnx` the canonical generator name.
- [ ] Add the missing `OnnxInferenceEngine` implementation with deterministic session close and file-path model loading.
- [ ] Validate every required graph one at a time and expose input/output names.
- [ ] Add tests for missing/empty files and canonical bundle completeness.
- [ ] Commit the model-runtime correction.

### Task 2: Build persistent Studio state
**Files:** `studio/StudioProfile.kt`, `studio/StudioProfileRepository.kt`, tests.

- [ ] Persist source URI, model bundle status, voice mode, output mode, RTMP endpoint, camera facing, quality preset.
- [ ] Restore state at startup and tolerate corrupt preferences by falling back to safe defaults.
- [ ] Persist URI permission only after successful source selection.
- [ ] Test save/reload and default behavior.

### Task 3: Replace one-file model import with bundle import
**Files:** `LivePortraitModelImporter.kt`, model UI/controller, tests.

- [ ] Accept multi-select ONNX files.
- [ ] Ignore unrelated files without deleting anything.
- [ ] Validate the complete bundle after import.
- [ ] Surface exact missing roles and graph-open failures.
- [ ] Preserve already installed models while replacing only selected matching files.

### Task 4: Implement LivePortrait engine boundaries
**Files:** `liveportrait/` classes and tests.

- [ ] Define appearance extractor, motion extractor, warping/SPADE, stitching, eye retargeting and lip retargeting interfaces.
- [ ] Create sessions lazily from files and release them together.
- [ ] Keep tensor conversion and image normalization isolated from Android UI.
- [ ] Refuse startup if graph input/output contracts do not match the supported model manifest.
- [ ] Add deterministic engine lifecycle tests.

### Task 5: Implement driver motion and bounded frame processing
**Files:** `camera/`, `streaming/`, existing `FramePipeline`/analyzer.

- [ ] Capture front-camera frames with latest-frame-wins behavior.
- [ ] Track the driver's face and required landmarks without retaining camera frames.
- [ ] Feed motion to the LivePortrait engine on a dedicated processing executor.
- [ ] Drop stale frames under load rather than building a queue.
- [ ] Add lifecycle tests for start/stop/release.

### Task 6: Build Studio outputs
**Files:** `output/`, `streaming/`.

- [ ] Define output sink interface with preview, RTMP and MyCam implementations.
- [ ] Make preview independent from RTMP/MyCam failures.
- [ ] Expose a stable MyCam frame bridge and explicit unavailable/error states.
- [ ] Keep audio and video clocks separate until an A/V sync layer joins them.
- [ ] Add output lifecycle tests.

### Task 7: Voice/lip-sync foundation
**Files:** `voice/` and tests.

- [ ] Add Normal, Male, Female and Imported Voice profile modes.
- [ ] Add microphone capture abstraction and lip-sync timing interface.
- [ ] Do not claim neural conversion unless an actual converter is present and device-tested.
- [ ] Make unsupported conversion modes report a clear setup error instead of silently falling back.

### Task 8: Replace old MainActivity/UI with Studio Home
**Files:** `MainActivity.kt`, `activity_main.xml`, UI resources.

- [ ] Remove active ArcFace/INSwapper import controls from the main flow.
- [ ] Add one-time Studio setup actions: Source, Models, Voice, Camera, Output.
- [ ] Restore all persisted selections on launch.
- [ ] Keep lock-screen/passcode behavior unchanged.
- [ ] Make Start Live transactional and display exact blocking reason when setup is incomplete.

### Task 9: Release workflow and artifact naming
**Files:** `.github/workflows/android.yml`, release docs.

- [ ] Build and test the debug APK as before.
- [ ] Rename the final APK to `Kémzyavatarstudio.apk`.
- [ ] Generate `Kémzyavatarstudio.apk.sha256`.
- [ ] Upload both as the single final artifact.
- [ ] Fail the workflow when either file is missing.

### Task 10: Full verification gate
- [ ] Run all JVM tests.
- [ ] Run Gradle `test assembleDebug` in GitHub Actions.
- [ ] Inspect failed job logs if CI fails and correct the actual cause.
- [ ] Verify artifact name and SHA-256 from the completed run.
- [ ] Install-test on the Infinix Smart 10.
- [ ] Verify passcode, persistent source, model validation, camera start, Live start, preview and stop.
- [ ] Verify MyCam bridge behavior on the target device before making any WhatsApp compatibility claim.
- [ ] Only then declare the APK ready for the user's single download.
