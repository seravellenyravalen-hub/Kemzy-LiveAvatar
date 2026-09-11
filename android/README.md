# Kemzy-LiveAvatar Android bridge

This directory is an additive Android 15+ client/runtime layer for the upstream Deep-Live-Cam tree.

## Repository boundary

- Existing upstream files outside `android/` are not edited, renamed, deleted, or reformatted by the Android layer.
- Desktop Python functionality remains available from the repository root.
- Android processing uses real compatible models; unsupported model graphs or features fail explicitly rather than rendering a mock result.

## Runtime pipeline

The Android live path is structured as:

`CameraX frame -> face detection/landmarks -> landmark alignment -> 512-D source embedding -> INSwapper 128x128 inference -> affine paste-back -> feathered face mask -> transformed frame`

`LiveSwapProcessor` is the coordinator for this path. `ArcFaceEmbedder` prepares a normalized 112x112 face and produces the 512-D identity vector. `InswapperOnnx` executes the two-input INSwapper graph and returns the 128x128 swapped face. `FaceCompositor` maps the swapped result back into the original camera frame.

The detector remains an explicit model adapter boundary because InsightFace's desktop `FaceAnalysis` bundle is not an Android API. The Android runtime must be given a compatible detector/landmark implementation rather than silently substituting a fake or geometric face.

## Models

The Android model bundle requires three roles:

- `face_detector.onnx` — real face detection + five landmarks (or an adapter for an equivalent compatible graph)
- `arcface_112.onnx` — 112x112 ArcFace-compatible recognizer producing a 512-value identity embedding
- `inswapper_128.onnx` or `inswapper_128_fp16.onnx` — 128x128 INSwapper graph

Weights are intentionally not copied into the upstream desktop `models/` directory and are never auto-downloaded by the Android code. Use the Android model import path for locally supplied, appropriately licensed weights.

## Runtime constraints

- CameraX supplies live frames with `STRATEGY_KEEP_ONLY_LATEST` to avoid an unbounded backlog.
- ONNX Runtime Android is used for compatible mobile graphs.
- CPU fallback is explicit; hardware acceleration is capability-dependent.
- The app does not pretend that a virtual/system camera exists when the device does not expose one.
- Minimum Android API: 35 (Android 15).

## Verification

The repository contains a GitHub Actions Android workflow under `.github/workflows/android.yml`. It uses JDK 17, Android API 35, Build Tools 35.0.0, and Gradle 8.13 so verification does not require installing an Android SDK on the development phone.
