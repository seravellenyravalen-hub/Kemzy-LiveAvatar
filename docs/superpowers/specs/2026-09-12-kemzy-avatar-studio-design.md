# Kémzy Avatar Studio Design

## Goal
Turn the Android app into a persistent, desktop-style LivePortrait studio for Kémzy àvátâr, using a selected source portrait driven continuously by the phone camera, with Preview/RTMP/MyCam output contracts and a persistent setup workflow.

## Product contract
- Display name: Kémzy àvátâr.
- Final APK filename: `Kémzyavatarstudio.apk`.
- Application ID: `com.kemzy.liveavatar`.
- Passcode-only unlock: `115522`; no biometric unlock.
- Existing Kémzy launcher icon remains unchanged.
- Existing `inswapper_128.onnx` is not part of the LivePortrait runtime and must not be deleted.
- Large LivePortrait model binaries remain outside Git and are imported once into private app storage.
- Live mode is allowed only after the complete validated model bundle is installed.

## Runtime architecture
CameraX captures frames into a bounded, latest-frame-wins pipeline. A face/motion tracker extracts driver motion. The LivePortrait engine owns model sessions and transforms the persisted source portrait using appearance extraction, motion extraction, warping/SPADE generation, stitching, and eye/lip retargeting. Output is emitted through independent sinks: local preview, RTMP, and a MyCam bridge. Each sink must be optional and independently testable.

## Persistent Studio state
A `StudioProfile` stores source URI, model-bundle installation state/version, voice mode, selected output sinks, RTMP endpoint, camera preference, and quality preset. Source URI permissions are persisted. On reopen, the studio restores the profile and never requires re-importing an unchanged source/model bundle.

## Model contract
Canonical files:
- `appearance_feature_extractor.onnx`
- `motion_extractor.onnx`
- `warping_spade-fix.onnx` (canonical Android generator)
- `stitching.onnx`
- `stitching_eye.onnx`
- `stitching_lip.onnx`
- `landmark.onnx`
- `retinaface_det_static.onnx`
- `face_2dpose_106_static.onnx`

The validator must check presence, non-zero size, expected names, and the canonical generator name. Runtime session creation must use file paths, never `readBytes()`. Model loading must be lazy and closed deterministically.

## Voice contract
Voice is a pluggable pipeline with Normal/Passthrough, Male, Female, and Imported Voice profile modes. Microphone capture and lip-sync timing are independent from video generation. Unsupported neural voice conversion must fail visibly rather than pretending to work. The architecture must permit a validated device-capable converter later.

## MyCam/WhatsApp contract
Kémzy does not claim to be a system camera. It exposes a stable frame output bridge for MyCam. MyCam remains responsible for the device-level virtual-camera handoff into WhatsApp. The bridge must report unavailable/connected/error states and never block the local preview.

## Error and memory policy
- Never copy large model files into Java heap.
- Bounded frame queues prevent camera backlog.
- Recycle/close bitmaps and ORT sessions deterministically.
- If memory pressure occurs, reduce processing resolution/FPS before allocating larger buffers.
- Every startup failure reports the missing model/role or failing subsystem.
- Live start is transactional: either every required runtime component initializes or the session remains stopped and all partially created resources are closed.

## Verification gate
A release is not called complete until CI tests and Gradle build are green, the final artifact is named correctly, the SHA-256 is generated, and the APK is install-tested. Model files are separately integrity-validated on-device before Live mode is enabled. Full camera/live, preview, and output paths must be smoke-tested before release claims.
