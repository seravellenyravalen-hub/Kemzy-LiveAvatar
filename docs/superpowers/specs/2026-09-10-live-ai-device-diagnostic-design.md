# Live AI Device Diagnostic Design

## Status
Approved in conversation; implementation is intentionally not included in this commit.

## Goal
Create a real-device diagnostic for Kemzy that measures whether the Android phone can sustain the intended live AI pipeline and whether the device exposes a usable camera-output capability for external apps. The diagnostic must never report success from a mock avatar, prerecorded video, static image, or synthetic placeholder result.

The target flow is:

**CameraX camera → continuous frame pipeline → real ONNX inference → continuous transformed-frame path → Android camera-output capability → target-app test**

The diagnostic is a feasibility gate for the final Deep-Live-Cam-like experience. It is not itself the finished face-swap engine or a guarantee that every Android 15 device can provide a system-wide virtual camera.

## Scope

### Included
- Live CameraX capture with Preview and ImageAnalysis.
- Latest-frame backpressure so stale frames are discarded rather than accumulated.
- Real ONNX Runtime model loading from Kemzy's private model directory.
- Continuous inference timing and throughput measurement.
- Camera frame counters and dropped-frame counters.
- Explicit model/input/output errors.
- Device capability probe for native virtual-camera support, external camera exposure, and background processing.
- A diagnostic screen showing camera, AI engine, live output capability, and result status.
- A physical-device acceptance test that checks whether a supported target app can actually select/use Kemzy's output.

### Excluded
- Copying or embedding proprietary MYCAM source, APK, or implementation details.
- Bypassing Android or target-app camera security.
- Claiming a normal APK is a system camera provider when the OEM/firmware does not expose the required capability.
- Cloud-only inference as the success path.
- Fake face-swap output used to make the diagnostic appear successful.
- Prerecorded video presented as live AI output.

## Architecture

### 1. Camera input
CameraX owns the physical camera lifecycle. `Preview` remains bound to a `PreviewView` so the user can see the real camera. `ImageAnalysis` uses `STRATEGY_KEEP_ONLY_LATEST` and a bounded pipeline. This is appropriate for live ML because CameraX explicitly drops stale frames under this strategy and requires each `ImageProxy` to be closed promptly.

The analyzer records:
- camera frames received
- frames accepted for processing
- frames dropped because the pipeline is busy
- frame conversion errors
- most recent frame timestamp

### 2. AI engine
The existing `InferenceEngine` / `OnnxInferenceEngine` boundary remains the model execution boundary. A diagnostic runner will load a real installed ONNX model and execute it continuously against compatible input tensors.

The runner records:
- model name
- model load success/failure
- input/output tensor metadata
- inference duration per processed frame
- rolling average inference latency
- processed FPS
- inference exceptions

A model is considered valid only when it loads and executes successfully with the expected input contract. A model that merely opens as an ONNX file but cannot accept the live frame contract is a failure, not a pass.

### 3. Live metrics
A thread-safe metrics collector will maintain rolling counters and recent timing samples. The UI refreshes periodically rather than once per frame.

Required fields:
- Camera FPS
- AI model name
- inference milliseconds
- processed FPS
- dropped frames
- native virtual camera: YES/NO/UNKNOWN
- external camera: YES/NO/UNKNOWN
- background processing: YES/NO/UNKNOWN
- AI performance: PASS/TOO SLOW/NOT TESTED
- camera output: PASS/NOT AVAILABLE/NOT TESTED

### 4. Output capability
Kemzy probes only capabilities actually exposed by the device/Android environment. The diagnostic distinguishes:

1. Native virtual camera: the OS/device exposes a usable virtual-camera route.
2. External camera: the environment exposes an external camera path, which is not automatically equivalent to a Kemzy virtual camera.
3. Background processing: Kemzy can continue camera/AI work in its foreground service, but this does not mean another app can consume the processed stream as a camera.
4. Unavailable: no supported output route is exposed.

The app must not claim system-wide camera output solely because it can keep processing frames in the background.

### 5. Target-app proof
The final output test is performed on the physical Infinix device. If the device exposes a supported native camera route, the user can test one consenting target app such as WhatsApp. Success requires the target app to actually see/select the Kemzy camera/output and receive moving transformed content.

If the target app does not expose Kemzy, the diagnostic reports `NOT AVAILABLE`; it must not attempt a security bypass.

## Diagnostic UI

The screen will present:

```text
Kemzy Test

CAMERA
✓ CameraX live frames
FPS: --

AI ENGINE
Model: --
Inference: -- ms
Processed FPS: --
Dropped: --

LIVE OUTPUT
Native virtual camera: YES / NO
External camera: YES / NO
Background processing: YES / NO

RESULT
AI performance: PASS / TOO SLOW
Camera output: PASS / NOT AVAILABLE
```

The UI is intentionally diagnostic rather than decorative. It should make it obvious whether a failure is camera capture, model compatibility, inference performance, or external camera capability.

## Performance criteria

The diagnostic does not hard-code an unsupported promise such as 30 FPS. It measures the actual device.

Initial result classification:
- `PASS`: real model inference is stable and the measured processed FPS is usable for a live preview according to the selected test threshold.
- `TOO SLOW`: the real model runs, but sustained latency/throughput is insufficient for the selected live threshold.
- `NOT TESTED`: no compatible model is installed or the test has not run.

The exact threshold remains configurable so the same diagnostic can be used on low-end and higher-end devices without falsely treating a single FPS number as universal.

## Model handling

Models remain outside the application source tree and are stored in Kemzy's private model directory. The diagnostic may discover installed `.onnx` files and allow selection of a model that is already on the device.

No large model is downloaded automatically as part of this diagnostic. If the device has no compatible real model, the UI must explain that the AI performance test cannot run until the required model is installed.

## Error behavior

Failures are explicit:
- camera permission denied → camera test unavailable
- frame conversion failure → camera/frame error
- missing model → AI not tested
- invalid ONNX input contract → model incompatible
- inference exception → AI test failed
- output capability absent → camera output not available
- target app cannot select/use the output → camera output not available

There is no fallback to a fake avatar, static source image, or prerecorded video.

## Acceptance criteria

1. On the physical Android 15 test device, Kemzy shows a moving real CameraX preview.
2. The analyzer receives continuous frames without unbounded queue growth.
3. A real compatible ONNX model can be loaded from local storage.
4. Continuous inference produces measured latency and processed-FPS values.
5. Dropped frames are measured rather than hidden.
6. The diagnostic distinguishes AI performance from camera-output capability.
7. Background processing is not reported as a virtual camera by itself.
8. If a native virtual-camera route is available, the device test can determine whether a supported target app can actually consume it.
9. If no usable camera-output route exists, the diagnostic reports that fact clearly instead of bypassing Android security.
10. The implementation remains independent of MYCAM's proprietary source/APK and does not copy or repackage it.

## Known limitations

- Android 15 does not guarantee that an ordinary third-party APK can publish a system-wide camera provider. OEM firmware and device capabilities determine what is exposed.
- ONNX Runtime execution speed depends on the model, input size, CPU/GPU/NPU support, thermal state, and Android device implementation. Real-device measurement is required.
- A successful AI benchmark does not by itself prove WhatsApp compatibility.
- A successful output-capability probe does not by itself prove that every target app will accept the output.
- The diagnostic is a foundation and feasibility gate; the full face-swap pipeline still requires the actual face detection, alignment, source identity/embedding, swap model, and compositing stages to produce transformed live frames.

## Verification strategy

Unit tests cover metrics calculations and result classification. Android tests cover model-discovery/error paths where practical. Device verification covers continuous camera capture, real model inference, background behavior, output capability, and a consenting target-app test. Completion claims require observed verification results; source code presence alone is not considered proof of live output.
