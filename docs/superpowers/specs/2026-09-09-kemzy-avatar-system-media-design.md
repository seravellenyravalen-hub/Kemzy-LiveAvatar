# Kémzy àvátâr — System Media Design

## Goal
Build Kémzy àvátâr as a smooth, session-locked live avatar engine whose camera and voice pipelines remain active after the Activity leaves the foreground, with a system-integration boundary for exposing the generated camera and PCM audio as virtual devices to other apps on compatible Android system builds.

## Product behavior
1. The launcher name is **Kémzy àvátâr**.
2. The app uses a custom branded icon rather than the previous generic LiveAvatar icon.
3. The user selects/imports one reference image before starting Live.
4. Kémzy copies the reference into app-private storage and locks that immutable local asset for the session.
5. While Live is active, network changes, Activity recreation, backgrounding, or model retries cannot replace the locked reference.
6. Kémzy owns a foreground camera session while Live is active and continues processing after the Activity leaves the foreground.
7. Camera processing drops stale frames rather than building latency; inference work is serialized and bounded.
8. Voice supports live microphone capture and imported local recordings. Voice source selection is locked for the active session.
9. Voice audio is represented internally as PCM with an explicit sample rate/channel contract.
10. On stock Android, the app never claims to be a system-wide microphone when Android does not expose a supported system integration point.
11. On a compatible AOSP/custom system image, the system-side bridge exposes Kémzy PCM as a virtual input device and the generated avatar frames through the system virtual-camera path so normal camera/microphone consumers can select/use them.
12. Camera/microphone activity remains transparent to the user through Android's required foreground-service and privacy indicators; the system integration must not bypass app permission or privacy controls.

## Architecture
### App layer
`MainActivity -> LiveSessionCoordinator -> AvatarStreamingService`

Camera path:
`CameraX -> FaceTracker -> OnDeviceFaceSwapEngine -> StreamingFrameBus -> SystemVirtualCameraBridge`

Voice path:
`Microphone/ImportedVoice -> VoiceSource -> VoiceSessionController -> PCM -> SystemVirtualMicrophoneBridge`

The app layer owns session state, local assets, lifecycle, recovery, and user controls. It does not attempt hidden microphone injection into unrelated applications.

### System layer
For Android 14+ AOSP targets, the audio side follows the AIDL Audio HAL boundary. The system integration exposes a virtual input stream/device and consumes PCM supplied by the Kémzy system bridge. Audio policy must describe the virtual input so AudioFlinger/framework clients can discover and open it. AOSP documents that the Audio HAL is the boundary between `android.media` and device audio, and that Android 14+ uses AIDL for the Audio HAL. citeturn0search0turn0search10

The camera side uses the Android virtual-camera/system integration where supported. Virtual-device camera support is device/configuration dependent, so the app must capability-detect instead of assuming availability. citeturn0search6turn0search9

## Reliability requirements
- Reference asset must be private and immutable during Live.
- Voice source must be immutable during Live.
- Explicit Stop is the only normal path that releases camera, voice, reference, and system bridges.
- Camera and voice failures are retried with bounded backoff and circuit protection.
- The UI must not expose raw model stack traces or internal fallback diagnostics.
- The pipeline must never substitute the user's unprocessed face when the selected avatar is unavailable.
- Frame queues are bounded to one latest frame; audio uses a bounded ring buffer with underrun/overrun counters.
- Long-running inference and capture are isolated from the main thread.
- Model initialization is performed before entering active output mode whenever possible.
- Thermal/device overload triggers quality throttling rather than unbounded work.
- Session teardown is idempotent.

## Performance targets
- No unbounded camera queue.
- No unbounded PCM queue.
- Main thread never performs ONNX inference or audio encoding.
- Prefer stable frame cadence over processing every camera frame.
- Reuse buffers where safe and release native/ML resources on explicit Stop.
- Measure camera processing latency and audio buffer health internally.

## Compatibility modes
### Stock Android
- Kémzy foreground camera/voice processing works inside the app and survives Activity backgrounding subject to Android permissions/foreground-service rules.
- Generated frames/audio are available to Kémzy's own UI and local playback/recording.
- System-wide virtual microphone/camera is reported as unavailable unless a real supported system integration is present.

### Compatible system build
- A privileged/system component or vendor/AOSP audio HAL exposes the virtual microphone.
- A compatible virtual-camera system component exposes generated frames.
- Other apps use normal Android camera/microphone APIs and see the system-provided virtual devices according to device policy.

## Security and privacy
- No covert capture.
- Camera/microphone activation is user initiated and visibly indicated.
- System bridge access is authenticated/permissioned; arbitrary apps cannot inject PCM into Kémzy's virtual microphone.
- The reference image and voice assets remain app-private unless the user explicitly exports them.
- System bridge disconnects fail closed rather than falling back to raw microphone/camera output.

## Branding
- Display name: **Kémzy àvátâr**.
- Icon concept: a clean premium avatar silhouette/face inside a soft geometric frame, with a subtle dual-wave motif representing motion + voice. Vector-first so it remains sharp at launcher and notification sizes.

## Additional hardening included in scope
- Session coordinator for one source of truth.
- Immutable local reference/voice asset handles.
- Capability detection for virtual camera and virtual microphone.
- Audio format contract and bounded PCM ring buffer.
- Foreground-service camera + microphone declarations where background capture is enabled.
- Watchdog/retry handling for camera, model, audio bridge, and system bridge.
- Thermal-aware quality policy.
- Internal health metrics without exposing diagnostics in the normal UI.
- Unit tests for session locking, queue bounds, bridge state transitions, and teardown idempotency.
- Integration documentation and AOSP-side implementation boundary for the system image.

## Non-goals
- Pretending a normal APK can install a global microphone on arbitrary stock Android devices.
- Bypassing another app's microphone permission.
- Replacing Android privacy indicators.
- Downloading a large voice-cloning model merely to make ordinary recording/import work.
- Claiming a system virtual device is available until the device actually exposes the required integration.
