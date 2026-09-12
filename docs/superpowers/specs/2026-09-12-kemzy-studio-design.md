# Kémzy Studio Design

## Goal
Turn Kémzy àvátâr into a persistent live-avatar studio: configure a source person, verified LivePortrait model bundle, voice profile, camera, and output once; reopen the app later and start the same live session without repeated imports.

## Product contract
- App name: Kémzy àvátâr.
- Passcode-only lock: `115522`.
- No biometric unlock.
- Existing #286 branding/icon remains the visual baseline.
- Source image/profile is persistent.
- Model binaries remain outside Git/APK and are imported once into app-private storage.
- Model bundle is validated before Live can start.
- Live processing is continuous and driven by the device camera, not a one-shot face swap.
- Video output is separated from the AI engine so the same processed stream can feed preview, RTMP, and the MyCam integration layer.
- Voice is a separate synchronized pipeline. Voice conversion must never be represented as working until its real-time implementation is tested on the target device.
- MyCam is the virtual-camera handoff layer. Kémzy produces the processed media; MyCam exposes it to compatible consumer apps such as WhatsApp.

## Architecture

```text
Studio Profile
  ├─ Source Profile
  ├─ Model Bundle
  ├─ Voice Profile
  ├─ Camera Profile
  └─ Output Profile
          │
          ▼
   Live Session Controller
          │
    ┌─────┴─────┐
    ▼           ▼
Video Pipeline Audio Pipeline
    │           │
    ▼           ▼
LivePortrait   Voice Engine
    │           │
    └─────┬─────┘
          ▼
     A/V Sync Layer
          │
          ▼
    Output Multiplexer
    ├─ Preview
    ├─ RTMP
    └─ MyCam Bridge
             │
             ▼
        Virtual Camera
             │
             ▼
          WhatsApp
```

## LivePortrait model manifest
The Android bundle is exactly nine ONNX files:

- `appearance_feature_extractor.onnx`
- `motion_extractor.onnx`
- `warping_spade-fix.onnx` (canonical Android bundle filename)
- `stitching.onnx`
- `stitching_eye.onnx`
- `stitching_lip.onnx`
- `landmark.onnx`
- `retinaface_det_static.onnx`
- `face_2dpose_106_static.onnx`

The manifest must be explicit about expected filename, minimum non-zero size, and SHA-256. The app must validate every file and open each model through ONNX Runtime one at a time before reporting READY.

## Persistence
Use a versioned JSON-backed StudioProfile in app-private storage. Persist only configuration and URI references, never large bitmaps or model bytes. A source URI must be backed by persistable read permission or copied into app-private source storage. Imported voice assets must likewise be copied to private storage.

## Reliability gates
1. No Live start with an incomplete model bundle.
2. No Live start with an invalid ONNX graph.
3. No large model `readBytes()` allocations.
4. All long-running inference occurs off the UI thread.
5. Frame queues are bounded to prevent memory growth.
6. Output sinks can fail independently without crashing the inference engine.
7. Stopping Live closes all ONNX sessions and releases frame buffers.
8. App backgrounding stops the live session cleanly.
9. Persisted configuration is schema-versioned and safely defaults when corrupted.
10. CI must run unit tests and assemble the APK before an artifact is offered for download.

## MyCam contract
Kémzy exposes a `VirtualCameraBridge` interface. The bridge receives immutable video frames and audio packets from the output multiplexer. The implementation is capability-aware and reports `UNAVAILABLE`, `READY`, `RUNNING`, or `ERROR`; it must not claim system-camera availability when the device/helper cannot provide it.

## Voice contract
`VoiceEngine` consumes microphone PCM and exposes processed PCM plus timing metadata. Initial supported sources are normal microphone passthrough and imported audio/profile configuration. Male/female neural conversion is a pluggable engine and is not marked production-ready until a tested real-time implementation is available on the target device. Lip-sync receives the same timing clock as the audio pipeline.

## Definition of done
The project is not considered complete merely because it compiles. Completion requires: unit tests green; CI build green; exact APK artifact tied to the tested commit; model manifest published with verified hashes; local model validation passes; Studio persistence survives restart; LivePortrait inference path is exercised on the target phone; and MyCam/WhatsApp handoff is tested separately on the target phone. Any unsupported device-specific capability must be surfaced explicitly rather than hidden behind a fake success state.
