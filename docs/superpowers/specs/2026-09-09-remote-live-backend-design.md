# Remote Live Avatar Backend Design

**Date:** 2026-09-09

## Goal
Move all face-swap model inference out of the Android device and make Kemzy operate over the Internet using a real low-latency processed video stream, while keeping voice out of this phase.

## Requirements
- Android captures the user's camera feed.
- Camera frames travel over the Internet to a remote backend.
- The backend owns and loads the Deep-Live-Cam/InsightFace processing models.
- The backend performs the actual live face swap and returns the processed video stream.
- Android renders the processed stream as the live avatar output.
- The selected reference image is uploaded to the backend for the active session; model weights are never downloaded to the phone.
- Start/stop and connection state must reflect the remote pipeline rather than claiming local inference is active.
- Recording/import/playback must use the processed output where the existing app supports those flows.
- Background live mode must use the same remote processing pipeline.
- The existing local CameraX path remains only as a controlled fallback/diagnostic path; it is not the primary model path.
- Voice processing is explicitly out of scope.
- No fake virtual-camera success state is allowed.

## Architecture

```text
Kemzy Android
  CameraX capture
       |
       | WebRTC over Internet
       v
Remote signaling / session API
       |
       v
GPU inference worker
  Deep-Live-Cam processing tree
  InsightFace / ONNX models
       |
       | processed WebRTC video
       v
Kemzy Android renderer
       |
       +--> live preview / recording
       |
       +--> supported virtual-camera integration
```

WebRTC is the primary media transport because the requirement is live Internet video, not periodic image uploads. The control plane exposes health, session, reference-image upload, and start/stop operations. The media plane carries the camera track to the GPU worker and the processed track back to Android.

## Backend

The backend is a separately deployable Python service. It should use the upstream Deep-Live-Cam processing modules rather than a mock or unrelated face-swap implementation. Model weights are obtained at deployment/runtime from their permitted upstream locations and are not committed to Git.

A GPU-capable deployment target is required for useful real-time performance. A general CPU-only Render/Railway instance may host signaling/control, but it must not be presented as equivalent to a GPU inference worker.

The first implementation should provide:
- `/health` for service/model readiness.
- Session creation and authenticated session token.
- Reference image upload for a session.
- WebRTC offer/answer signaling.
- A video transform that consumes the Android camera track and emits the processed track.
- Explicit model-loading and inference errors.
- Session cleanup and resource limits.

## Android

Add a remote streaming client that owns the WebRTC peer connection and exposes a small state machine: DISCONNECTED, CONNECTING, CONNECTED, PROCESSING, FAILED, STOPPED. The existing CameraX capture path supplies the local video track. The remote video track is rendered into the main live surface.

The UI must distinguish:
- camera permission/readiness;
- Internet/backend connectivity;
- reference-image readiness;
- model readiness;
- processed-frame availability;
- virtual-camera capability.

The Android app must not report `Live camera ready` merely because CameraX is producing frames. It should report processed readiness only after remote processed frames arrive.

## Virtual camera

The implementation must not fake or spoof a system camera. Android's normal application sandbox does not allow an ordinary APK to register an arbitrary system-wide camera device for applications such as WhatsApp. The existing `VirtualCameraBridge` is retained only where the target OS actually grants the required VirtualDevice/VirtualCamera capability. Registration failures must remain explicit.

The remote processed video should nevertheless be exposed through a clean internal video-output interface so a real system-level virtual-camera provider, when available on a supported build/device, can consume exactly the processed frames. If the stock device rejects virtual-camera registration, Kemzy must show that limitation instead of claiming registration succeeded.

## Data flow

1. User selects a reference image.
2. Kemzy creates a remote session over HTTPS.
3. Kemzy uploads the reference image over the authenticated session.
4. Backend loads/validates the selected Deep-Live-Cam processing components.
5. Kemzy establishes a WebRTC peer connection.
6. CameraX sends live video to the backend.
7. GPU worker performs face detection/alignment/swap/restoration using the upstream processing path.
8. Backend returns processed video through WebRTC.
9. Kemzy renders returned frames in the live surface.
10. Recording, when requested, records the processed output.
11. Stop tears down WebRTC and backend resources.

## Security and privacy

- Use HTTPS/WSS for signaling and authenticated WebRTC sessions.
- Do not store reference images permanently unless explicitly required later.
- Do not put model credentials or service secrets in the APK.
- Do not commit model weights or private deployment secrets.
- Limit session lifetime and clean up media resources on disconnect.

## Testing

Backend tests must cover health/model readiness, session lifecycle, reference-image validation, signaling, and a deterministic frame-transform test using a small test fixture.

Android tests must cover remote-state transitions, session start/stop, failure handling, and the rule that processed readiness requires a received processed frame. Existing virtual-camera tests must continue to reject unsupported platform registration rather than reporting success.

An end-to-end test should be performed on a real Android device using a public HTTPS backend endpoint and a GPU worker. Completion means the Android camera feed reaches the backend over the Internet and a visibly processed video track returns to the phone.

## Non-goals

- Voice changer or voice effects.
- Local face-swap model inference on Android.
- Pretending a network URL is an Android system camera.
- Unverified claims that WhatsApp can select a virtual camera on stock Android.
