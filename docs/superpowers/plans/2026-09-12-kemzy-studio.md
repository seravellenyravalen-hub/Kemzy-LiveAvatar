# Kémzy Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent Kémzy àvátâr Studio around a verified LivePortrait pipeline, synchronized voice/output architecture, and a MyCam integration boundary while preserving the existing #286 branding and passcode behavior.

**Architecture:** A versioned StudioProfile owns source/model/voice/camera/output configuration. A LiveSessionController coordinates bounded camera frames, LivePortrait inference, audio/voice timing, and independent output sinks. Large ONNX binaries stay outside Git/APK and are validated file-by-file through ONNX Runtime before Live starts.

**Tech Stack:** Kotlin, Android CameraX, ONNX Runtime, existing Kémzy repositories/services, GitHub Actions/Gradle.

**Spec:** `docs/superpowers/specs/2026-09-12-kemzy-studio-design.md`

## Global Constraints

- App name: Kémzy àvátâr.
- Passcode: `115522`.
- Passcode-only; no biometric unlock.
- Preserve #286 branding/icon baseline.
- Do not package the large ONNX models inside the APK.
- Never load large ONNX models with `readBytes()`.
- Do not keep unbounded camera/audio queues.
- Do not claim MyCam/WhatsApp compatibility without target-device verification.
- Do not mark neural voice conversion complete until real-time target-device testing passes.
- Every production APK must come from a successful CI run tied to the exact source commit.

---

### Task 1: Lock the canonical model manifest

**Files:**
- Modify: `android/app/src/main/java/com/kemzy/liveavatar/LivePortraitModelSpec.kt`
- Test: `android/app/src/test/java/com/kemzy/liveavatar/LivePortraitModelSpecTest.kt`
- Modify: `docs/superpowers/specs/2026-09-12-kemzy-studio-design.md`

- [ ] Write tests for exactly nine required names and deterministic ordering.
- [ ] Change the canonical warping filename to the selected `warping_spade-fix.onnx` bundle name.
- [ ] Add manifest metadata hooks for expected minimum size and SHA-256.
- [ ] Keep model binaries outside Git.
- [ ] Run model-spec tests.
- [ ] Commit.

### Task 2: Make bundle import atomic

**Files:**
- Modify/Create: `android/app/src/main/java/com/kemzy/liveavatar/LivePortraitModelImporter.kt`
- Modify: `ModelRepository.kt`
- Test: `LivePortraitModelImporterTest.kt`

- [ ] Write failing tests for incomplete import, duplicate replacement, and atomic commit.
- [ ] Implement staging into a temporary private directory.
- [ ] Validate all nine files before moving them into the active model directory.
- [ ] Never delete the previous verified bundle until the replacement is verified.
- [ ] Add a persisted bundle version/fingerprint.
- [ ] Run tests.
- [ ] Commit.

### Task 3: Strengthen ONNX validation

**Files:**
- Modify: `LivePortraitModelValidator.kt`
- Modify: `OnnxInferenceEngine.kt`
- Test: `LivePortraitModelValidatorTest.kt`

- [ ] Test missing, zero-byte, corrupt, and valid files.
- [ ] Validate each graph by path, one at a time.
- [ ] Record input/output names without retaining sessions.
- [ ] Add manifest hash/size verification before graph loading.
- [ ] Ensure failures identify the exact model.
- [ ] Run tests.
- [ ] Commit.

### Task 4: Build persistent StudioProfile

**Files:**
- Create: `studio/StudioProfile.kt`
- Create: `studio/StudioProfileRepository.kt`
- Create: `studio/StudioProfileStore.kt`
- Test: `studio/StudioProfileRepositoryTest.kt`

- [ ] Write tests for first-run defaults and round-trip persistence.
- [ ] Store source, model bundle fingerprint, voice profile, camera, quality, and output preferences.
- [ ] Version the persisted schema.
- [ ] Recover safely from corrupted settings.
- [ ] Keep large binary assets out of preferences.
- [ ] Run tests.
- [ ] Commit.

### Task 5: Migrate the source profile to persistent storage

**Files:**
- Modify: `SourceFaceRepository.kt`
- Modify: `MainActivity.kt`
- Test: `SourceFaceRepositoryTest.kt`

- [ ] Write tests for persisted source selection.
- [ ] Copy selected source media into private storage when persistable URI permission is insufficient.
- [ ] Restore source automatically on app start.
- [ ] Keep bitmap lifetime bounded and recycle old source frames.
- [ ] Run tests.
- [ ] Commit.

### Task 6: Introduce the LivePortrait engine boundary

**Files:**
- Create: `liveportrait/LivePortraitEngine.kt`
- Create: `liveportrait/LivePortraitSession.kt`
- Create: `liveportrait/LivePortraitFrame.kt`
- Create: `liveportrait/LivePortraitMotion.kt`
- Test: `liveportrait/LivePortraitEngineContractTest.kt`

- [ ] Define exact session lifecycle: prepare, start, process, stop, close.
- [ ] Define bounded frame ownership and no-retention semantics.
- [ ] Inject the validated nine-model bundle.
- [ ] Keep detector/landmark/motion/warping/generator/stitching/retargeting roles separate.
- [ ] Add a contract test for failure when the bundle is incomplete.
- [ ] Commit.

### Task 7: Implement the actual LivePortrait tensor adapters

**Files:**
- Create/Modify: `liveportrait/*Adapter.kt`
- Test: `liveportrait/*AdapterTest.kt`

- [ ] Inspect the exact ONNX graph interfaces before writing tensor mappings.
- [ ] Add shape/type validation for every model input/output.
- [ ] Implement preprocessing/postprocessing without whole-frame byte-array copies.
- [ ] Reuse direct/native buffers where safe.
- [ ] Test each adapter independently with known graph metadata.
- [ ] Commit.

### Task 8: Replace old INSwapper start path

**Files:**
- Modify: `MainActivity.kt`
- Modify: `activity_main.xml`
- Modify: live-session classes
- Test: `MainActivity`/controller tests where supported

- [ ] Remove the requirement for `w600k_r50.onnx` and `inswapper_128.onnx` from Live mode.
- [ ] Replace separate ArcFace/INSwapper import controls with one LivePortrait bundle action.
- [ ] Gate Start Live on complete validated LivePortrait bundle + source.
- [ ] Preserve the old implementation only where needed for migration, not as the new live path.
- [ ] Run Android tests and assemble.
- [ ] Commit.

### Task 9: Create the full Studio UI

**Files:**
- Modify/Create layouts and resources under `android/app/src/main/res/`
- Create `ui/*` classes as required
- Test: UI/controller tests where supported

- [ ] Build Home, Source, Models, Voice, Camera, Output, Stream, and Settings surfaces.
- [ ] Show clear Ready/Not Ready states.
- [ ] Do not expose controls that cannot work on the current device.
- [ ] Preserve Kémzy branding and custom icon.
- [ ] Commit.

### Task 10: Add voice pipeline contracts

**Files:**
- Create: `voice/VoiceProfile.kt`
- Create: `voice/VoiceEngine.kt`
- Create: `voice/MicrophoneInput.kt`
- Create: `voice/VoiceOutput.kt`
- Create: `voice/LipSyncClock.kt`
- Test: voice contract tests

- [ ] Implement normal microphone passthrough first.
- [ ] Add persistent male/female/imported profile configuration.
- [ ] Keep neural conversion behind an explicit engine interface.
- [ ] Ensure audio timestamps feed the lip-sync clock.
- [ ] Do not fake conversion success.
- [ ] Commit.

### Task 11: Build the output multiplexer

**Files:**
- Create: `output/LiveOutputSink.kt`
- Create: `output/OutputMultiplexer.kt`
- Modify: existing RTMP output
- Test: output sink lifecycle tests

- [ ] Define immutable video/audio packet contracts.
- [ ] Allow preview, RTMP, and MyCam sinks to run independently.
- [ ] Make sink failure non-fatal to the core inference session.
- [ ] Ensure stop closes every sink and releases buffers.
- [ ] Commit.

### Task 12: Add MyCam integration boundary

**Files:**
- Create: `output/VirtualCameraBridge.kt`
- Create: `output/MyCamBridge.kt`
- Create: `output/MyCamCapability.kt`
- Test: bridge contract tests

- [ ] Define explicit capability states.
- [ ] Do not report READY without a real bridge handshake.
- [ ] Keep the adapter independent from the LivePortrait engine.
- [ ] Add diagnostics for connection, frame flow, and audio flow.
- [ ] Commit.

### Task 13: Integrate RTMP without coupling it to preview/MyCam

**Files:**
- Modify: existing `RtmpLiveOutput.kt`
- Modify: `OutputMultiplexer.kt`
- Test: RTMP lifecycle tests

- [ ] Route the same timestamped output stream through RTMP.
- [ ] Ensure RTMP errors don't crash LivePortrait.
- [ ] Commit.

### Task 14: Harden lock lifecycle and persistence

**Files:**
- Inspect/Modify: `LockActivity.kt`, `KemzyApplication.kt`, privacy-lock classes
- Test: lock lifecycle tests

- [ ] Verify passcode `115522` behavior.
- [ ] Keep biometric disabled.
- [ ] Ensure backgrounding locks the Studio.
- [ ] Ensure reopening restores persisted configuration after unlock.
- [ ] Commit.

### Task 15: End-to-end reliability and CI gates

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: CI validation scripts/tests as required
- Modify: model manifest documentation

- [ ] Add unit-test, Android-test/assemble, lint where supported, and artifact checksum gates.
- [ ] Publish exact commit SHA with the APK checksum.
- [ ] Ensure artifact is named `kemzyavatar-apk` and APK is `kemzyavatar.apk`.
- [ ] Verify the workflow from the feature branch.
- [ ] Only after green CI, inspect the artifact and record its SHA-256.
- [ ] Commit.

### Task 16: Target-device acceptance

**Files:**
- Documentation only as required.

- [ ] Install the exact verified APK on the Infinix Smart 10.
- [ ] Verify unlock, persistent source, model validation, LivePortrait session, memory stability, preview, voice path, and stop/restart.
- [ ] Verify MyCam handshake and WhatsApp virtual-camera behavior separately.
- [ ] Verify RTMP if configured.
- [ ] Record any device-specific limitation instead of hiding it.

## Final release gate

The APK is offered to the user only when CI is green, the artifact belongs to the exact tested commit, the SHA-256 is recorded, and target-device acceptance has passed for the features claimed as working. Models are supplied separately with the exact manifest and checksums so the user can download once and verify before importing.
