# Kemzy-LiveAvatar Android bridge

This directory is an additive Android 15+ client/runtime layer for the upstream Deep-Live-Cam tree.

## Repository boundary

- Existing upstream files outside `android/` are not edited, renamed, deleted, or reformatted by the Android layer.
- Desktop Python functionality remains available from the repository root.
- Android processing must use real compatible models; unsupported model graphs or features fail explicitly rather than rendering a mock result.

## Runtime goals

The Android app uses CameraX for camera input and a bounded latest-frame pipeline for live processing. ONNX Runtime Android is used for model execution when the selected graph is compatible with mobile execution. CPU fallback is explicit; acceleration is capability-dependent.

Minimum Android API: 35 (Android 15).

Model weights are intentionally not copied into the upstream `models/` directory. Use the Android model manager/import path for locally supplied compatible weights.

## Android-wide camera output

Kemzy now has a capability-driven output layer rather than a WhatsApp-specific integration:

1. Physical front camera is captured by CameraX.
2. Live frames enter the bounded Kemzy processing pipeline.
3. A user-started camera foreground service can keep the physical camera and processing pipeline alive while another application is foreground.
4. Kemzy probes the device for Android virtual-camera/VirtualDeviceManager support and external camera devices.
5. If the device exposes a usable native virtual-camera provider path, that route is preferred.
6. If the OS exposes only background camera processing, Kemzy keeps processing but does **not** claim that another app can select the frames as a camera.

The target is application-independent camera output: any destination app that uses the Android camera framework and accepts the exposed camera device can potentially consume Kemzy. The implementation does not special-case WhatsApp, Telegram, Messenger, Meet, Zoom, browsers, or other individual apps.

### Important Android limitation

Android 15 includes virtual-camera functionality in the platform's VirtualDevice work, but the availability and permissions are device/OEM dependent. A normal APK cannot install a privileged Camera HAL or silently force another app to select a camera. Kemzy therefore detects the real capability and reports the state instead of presenting a fake camera.

The AOSP VirtualDeviceManager demos also show that virtual-camera functionality can involve system/privileged components on supported builds. If the Infinix firmware exposes the required public route, Kemzy can use it; otherwise a system/OEM provider integration would be required.

### Background camera rules

Background live mode must be started while Kemzy is visible and the user has granted camera permission. Android 14+ applies while-in-use camera permission and foreground-service restrictions, so Kemzy cannot legitimately start a camera service from an arbitrary background state and bypass those rules.
