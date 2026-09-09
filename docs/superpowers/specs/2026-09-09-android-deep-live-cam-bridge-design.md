# Kemzy-LiveAvatar Android Deep-Live-Cam Bridge Design

## Goal

Add a separate Android 15+ application layer to the repository so the Deep-Live-Cam functionality can be accessed from Android while the copied upstream Deep-Live-Cam tree remains unchanged.

## Non-negotiable repository boundary

- Do not edit, delete, rename, or reformat any existing Deep-Live-Cam file.
- All Android-specific code lives in newly added paths under `android/` plus this design/implementation documentation.
- Existing upstream Python behavior remains available for desktop users.
- The Android layer must not replace or fork the upstream implementation in-place.

## Product scope

The Android app, named Kemzy-LiveAvatar, should expose Android equivalents of the upstream capabilities where technically possible:

- source face selection/import
- live camera mode
- image/video target processing
- recording and playback of generated output
- face swapping using the same model family/workflow
- face enhancement where the required model can run on-device
- multiple-face processing/mapping where supported by the mobile pipeline
- mouth-mask behavior where supported by the mobile pipeline
- mirror/resizable live preview controls
- processing quality/FPS controls
- execution-provider/performance information and safe fallbacks
- model management/status
- permissions and device setup/help
- privacy lock/lifecycle reset requested for Kemzy-LiveAvatar

Unsupported desktop-only operations will be represented explicitly rather than silently pretending to work.

## Architecture

Create a self-contained Android Gradle project under `android/` targeting Android 15+ (minSdk 35). The Android UI and lifecycle layer uses native Android components and CameraX for camera preview/capture.

Processing is separated behind interfaces:

1. `CameraInput` — owns camera lifecycle and frames.
2. `SourceFaceRepository` — imports and preserves the selected source image.
3. `FaceDetectionEngine` — detects/aligns faces.
4. `FaceSwapEngine` — runs the compatible ONNX face-swap model.
5. `FaceEnhancementEngine` — optional enhancement stage.
6. `FramePipeline` — queues, throttles, drops stale frames, and manages backpressure.
7. `OutputRecorder` — records processed frames and supports playback/export.
8. `ModelRepository` — validates/downloads locally supplied model assets without changing upstream model files.
9. `PerformanceController` — selects CPU or supported Android acceleration and adapts resolution/FPS.

The UI depends only on these interfaces. The actual inference implementation is isolated from the UI so the model runtime can be changed without changing the rest of the app.

## Desktop compatibility

The repository's existing Python application remains the desktop implementation. The Android project is an additive client/runtime bridge, not a rewrite of the Python code. Shared model artifacts are treated as model inputs, not as modifications to the upstream source tree.

The Android pipeline will use ONNX Runtime Mobile/Android where the upstream ONNX graphs are compatible. Any incompatibility discovered during validation must fail clearly and offer a supported fallback rather than shipping a fake result.

## Performance

Android 15+ is the minimum supported OS. Performance controls will include camera/inference resolution, target FPS, frame skipping/backpressure, and CPU/accelerated execution selection when available. The app will avoid accumulating an unbounded frame queue.

Android 15+ does not guarantee zero lag on every device; the pipeline is designed to degrade gracefully on lower-end Android 15+ hardware.

## UI flow

- privacy lock screen on app launch/re-entry
- source/avatar selection with a compact full-image thumbnail
- main Live workspace with real camera preview and processed output
- controls for Live, Record, Stop, Import/Process, playback, source replacement, mirror, and relevant quality settings
- settings/help screen for permissions, models, acceleration, performance, and diagnostics
- explicit processing/error states

## Model handling

The existing repository does not contain the upstream ONNX weights. The Android layer must therefore provide a clear model-status/download/import path. Model files are stored outside the upstream source files and validated before use.

## Safety and consent

The app should preserve clear responsible-use messaging and require users to have authorization/consent for faces they process. Generated media should be identifiable as synthetic where it is shared. No implementation should bypass platform safeguards or facilitate impersonation, fraud, or unauthorized identity use.

## Testing

Tests will cover:

- source image preservation
- model availability/validation
- pipeline backpressure/frame dropping
- lifecycle start/stop/reopen behavior
- camera permission/error states
- deterministic face-swap engine contract with test fixtures where feasible
- recording/playback lifecycle
- Android build with minSdk 35
- repository-boundary verification that no existing upstream files changed

## Acceptance criteria

1. Existing upstream Deep-Live-Cam files are byte-for-byte unchanged by the Android addition.
2. Android project builds with minSdk 35.
3. Camera preview is a real CameraX preview, not a placeholder.
4. A valid source image can be selected and retained without replacing it with a generated substitute.
5. The inference layer uses real model execution when compatible models are installed; it never fakes a live result.
6. Live processing has bounded queues/backpressure and lifecycle-safe start/stop/reopen behavior.
7. Recording/import/playback flows have explicit success and failure states.
8. Desktop users can continue running the existing Python application unchanged.
9. Unsupported capabilities are reported honestly instead of being exposed as nonfunctional controls.
