# Android Virtual Camera Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Kemzy's remote processed video available through a real Android virtual-camera producer whenever the device platform permits it, with honest capability reporting otherwise.

**Architecture:** Separate platform capability detection, virtual-device registration, and frame production. The remote WebRTC/Deep-Live-Cam pipeline remains the source of processed frames; the virtual camera consumes those frames and exposes them through Android's camera framework only when registration succeeds.

**Tech Stack:** Kotlin, Android API 35/36, VirtualDeviceManager/VirtualCamera APIs where exposed, existing StreamingFrameBus, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-10-android-virtual-camera-design.md`

## Global Constraints

- Android 15+ is required for the virtual-camera path.
- Do not use MediaProjection as a camera substitute.
- Do not report REGISTERED until Android returns a real virtual camera.
- Do not claim WhatsApp/Telegram camera integration on unsupported stock devices.
- Keep remote Deep-Live-Cam processing on the backend; no local face-swap inference is added.
- Use TDD for behavior changes.

---

## Task 1: Define capability classification with a failing test

**Files:**
- Create `app/src/test/java/com/kemzy/liveavatar/VirtualCameraCapabilityTest.kt`
- Create `app/src/main/java/com/kemzy/liveavatar/VirtualCameraCapability.kt`

- [ ] Write tests for API below 35, unavailable VirtualDeviceManager, supported platform, and privileged registration failure classification.
- [ ] Run the Android unit test and confirm it fails because the capability classifier does not yet exist.
- [ ] Implement the smallest pure classifier and platform adapter needed by the tests.
- [ ] Run the unit test again and confirm it passes.
- [ ] Commit the capability layer.

## Task 2: Replace hidden reflection checks with an explicit registration adapter

**Files:**
- Modify `VirtualCameraBridge.kt`
- Add `VirtualCameraPlatformAdapter.kt` if needed

- [ ] Add a platform adapter boundary so device capability checks and actual registration are testable separately.
- [ ] Preserve the existing YUV_420_888 stream contract and 640x480/30fps baseline.
- [ ] Register only after the actual virtual-device and virtual-camera objects are created.
- [ ] Convert security/privilege failures to `PRIVILEGE_REQUIRED` instead of generic success/failure.
- [ ] Add tests for registration state transitions.
- [ ] Run all affected unit/instrumentation tests.
- [ ] Commit the registration changes.

## Task 3: Make frame production safe for virtual-camera consumers

**Files:**
- Modify `StreamingFrameBus.kt` if required
- Modify `VirtualCameraBridge.kt`
- Extend `VirtualCameraFrameBridgeTest.kt`

- [ ] Add a test proving the producer can consume a frame without invalidating the frame retained for Kemzy preview/recording.
- [ ] Run the test and confirm RED before changing production code.
- [ ] Implement independent bitmap copies and bounded stale-frame handling.
- [ ] Preserve YUV plane stride/pixel-stride handling.
- [ ] Run instrumentation tests and confirm GREEN.
- [ ] Commit the frame bridge.

## Task 4: Integrate remote processed output with virtual camera lifecycle

**Files:**
- Modify `AvatarStreamingService.kt`
- Modify `MainActivity.kt`
- Modify `RemoteLiveStreamState.kt`
- Add/update integration tests where practical

- [ ] Start virtual-camera registration only after the remote pipeline has produced an actual processed frame.
- [ ] Stop registration and release camera resources when the remote session ends.
- [ ] Ensure the UI distinguishes `REMOTE PROCESSING` from `VIRTUAL CAMERA READY`.
- [ ] Never mark WhatsApp/Telegram readiness based solely on WebRTC connection state.
- [ ] Run Android tests.
- [ ] Commit integration.

## Task 5: Device verification and documented platform result

**Files:**
- Update `backend/README.md` only if the deployment/runtime contract needs documentation.
- Add a short `docs/android-virtual-camera-verification.md` with device-test observations.

- [ ] Build the APK with the existing Android toolchain.
- [ ] Install on the target phone.
- [ ] Verify capability status in Kemzy.
- [ ] Verify the processed frame is visible in Kemzy.
- [ ] If the device exposes a usable system virtual camera, verify the camera appears to a compatible consumer app.
- [ ] If Android denies registration, record the exact platform/privilege reason instead of treating it as an app bug.
- [ ] Only then claim the virtual-camera path is working on that device.
