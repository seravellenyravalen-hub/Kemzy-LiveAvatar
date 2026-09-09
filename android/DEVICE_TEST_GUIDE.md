# Kemzy-LiveAvatar Android device test

## Install and permissions

1. Install the debug APK.
2. Open Kemzy-LiveAvatar and unlock it with the configured private passcode.
3. Allow Camera permission.
4. Allow Notifications so the foreground camera service can be observed.
5. Allow Microphone only when an audio feature explicitly requests it.
6. In Android battery settings, use Unrestricted/Don't optimize for Kemzy if the device offers that option.

## First test: local camera pipeline

1. Select a source face image.
2. Open Live.
3. Confirm the CameraX preview is moving and is not a black placeholder.
4. Confirm the live status is updating.
5. Start Background Live.
6. Leave Kemzy and open it again.
7. Unlock Kemzy. The foreground-service notification should still identify live camera processing when Background Live remains active.
8. Stop Background Live before testing another camera application.

## Virtual-camera capability test

Open Kemzy's camera/output diagnostics and read the reported route.

- Native virtual camera: the device exposes the required system capability. Test camera selection in a compatible app.
- External camera: the device exposes an external camera route; this is not the same as a Kemzy-created system virtual camera.
- Background processing: Kemzy can continue processing its own camera in the foreground service, but another app cannot automatically be forced to use those frames.
- Screen sharing: use only in apps that explicitly support screen sharing. Screen sharing is not a camera device.
- Unavailable: the device/OEM does not expose the required route to an ordinary app.

## App-by-app test

If the device reports a native virtual-camera route, test one app at a time:

1. Start Kemzy Background Live.
2. Open the target app.
3. Open that app's camera selector/settings.
4. If Kemzy-LiveAvatar is listed as an available camera, select it.
5. Start a private test call/preview with a consenting participant.
6. Confirm the video source is the processed Kemzy feed.
7. If Kemzy is not listed, do not try to bypass Android or the target app's camera security. Record the diagnostic result instead.

## Important limitation

Android 15 includes virtual-camera infrastructure, but an ordinary third-party APK cannot assume it has permission to publish a system-wide camera provider. OEM firmware and system/provider privileges determine whether that route is actually available.

## Model setup

The upstream Deep-Live-Cam project requires model files such as `inswapper_128_fp16.onnx`/`inswapper_128.onnx` and an enhancement model for its normal desktop workflow. The Android layer keeps model storage separate from the upstream repository tree. Do not place private model files into the upstream source tree unless you intentionally want them versioned.

The Android app must report model incompatibility or missing models instead of displaying fake processed output.

## Safety

Use only source faces and media you have permission to use. Clearly disclose synthetic/face-swapped media when appropriate. Do not use the app for impersonation, fraud, identity verification bypass, or unauthorized recording.
