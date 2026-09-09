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
