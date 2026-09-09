# Kémzy virtual camera — system integration boundary

The Android app can process camera frames and keep its foreground camera service alive, but an ordinary APK cannot arbitrarily replace another app's camera on every stock device.

## Compatible system path

`CameraX -> FaceTracker -> OnDeviceFaceSwapEngine -> bounded frame bus -> privileged virtual-camera producer -> CameraService/framework -> normal camera clients`

A compatible AOSP/custom build must provide the virtual-camera mechanism and connect it to Kémzy's generated frames. The app detects capability rather than assuming it exists.

## Requirements

- The system component must authenticate the Kémzy producer.
- Generated frames must be bounded and timestamped; stale frames are dropped.
- The virtual camera must expose a normal camera device/stream to permitted clients.
- Disconnect must stop the virtual camera rather than substitute the physical user camera.
- Camera privacy indicators and foreground-service behavior remain transparent.
- HAL/framework boundary input must be validated and sanitized.

The app-side `VirtualCameraBridge` is intentionally a capability boundary. Its stock implementation never pretends that `VirtualDeviceManager` alone gives a globally selectable camera to arbitrary applications.
