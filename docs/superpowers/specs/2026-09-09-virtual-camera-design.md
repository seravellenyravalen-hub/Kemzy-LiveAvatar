# Kemzy-LiveAvatar System-Wide Virtual Camera Design

## Goal
Expose Kemzy's generated live-avatar frames as a genuine Android camera device so camera clients that enumerate Android camera devices can select it. Preserve the existing in-app live avatar, microphone, recording, playback, settings, and privacy-lock flows.

## Non-goal
An ordinary APK cannot create a new Camera2/HAL camera device on a stock Android firmware. The system-wide path therefore has two layers: an app-side frame producer and a privileged/system camera-provider integration for a compatible rooted/custom-system build. The app must never report system-wide virtual-camera support unless the provider is actually registered and discoverable.

## Architecture
1. Physical front camera -> Kemzy CameraX acquisition.
2. FaceTracker -> identity-preserving OnDeviceFaceSwapEngine.
3. Generated frames -> shared virtual-camera frame transport.
4. Privileged camera provider/HAL -> exposes a virtual camera ID through Android's camera framework.
5. Camera2 clients -> consume the virtual device like a normal camera.
6. Kemzy UI continues to show the same generated frames and supports microphone, recording, playback, import, and settings.

## Android integration
For Android 13+ the camera HAL/provider interface uses AIDL. A provider must enumerate a device and expose device/session behavior through the Android camera framework. A production implementation also needs SELinux/RC/vendor integration and device-specific validation. The provider therefore belongs in an Android system/vendor build, not inside the normal application APK.

## Device capability behavior
The APK will implement a capability/status layer that distinguishes:
- In-app live avatar: supported.
- Background camera processing: supported when Android foreground-service rules permit it.
- System virtual camera provider: unavailable on ordinary stock firmware unless the firmware exposes an appropriate mechanism.
- System virtual camera provider: active only when the provider reports a registered camera device.

## Recording
CameraX VideoCapture must not be treated as avatar recording. The avatar recording path must encode the generated/composited frames. The physical-camera VideoCapture path may remain available for diagnostics until the generated-frame encoder is used for the user-facing recording action.

## Security/privacy
The existing passcode lock remains required. The system provider must not expose the user's reference image or raw camera stream. Stop/lock must terminate the frame producer and provider session. Microphone access remains explicit.

## Verification
1. Unit tests for capability state and provider status.
2. Build/test the Android APK.
3. On a compatible system image, verify the virtual camera appears in Camera2 enumeration.
4. Verify a camera client can open the virtual ID and receive generated frames.
5. Verify switching away from Kemzy does not incorrectly claim support when the system provider is absent.
6. Verify stop/lock tears down the virtual stream.

## Acceptance criteria
The project is only considered system-wide complete when an actual Android camera client enumerates and opens the Kemzy virtual camera. An APK-only build is explicitly not considered sufficient for that criterion.