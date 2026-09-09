# Remote Live Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Kemzy send Android camera video over the Internet to a GPU backend running the real Deep-Live-Cam/InsightFace pipeline and receive the processed live video back, with no voice processing in this phase.

**Architecture:** Android remains responsible for camera capture, UI, and output rendering. A remote Python WebRTC service owns the face-swap models and performs inference on a GPU; HTTPS handles session/control traffic and WebRTC carries live media in both directions. Virtual-camera output is exposed through a real capability-gated interface and never falsely reported as available on stock Android.

**Tech Stack:** Kotlin, CameraX, Android WebRTC client, Python, FastAPI, aiortc, OpenCV, InsightFace/ONNX Runtime, Deep-Live-Cam processing modules, Docker, GPU deployment.

**Spec:** `docs/superpowers/specs/2026-09-09-remote-live-backend-design.md`

## Global Constraints

- All face-swap model inference runs in the backend; Android does not download or execute the face-swap models.
- Media transport uses the Internet; localhost is not the production path.
- Voice processing is out of scope.
- Deep-Live-Cam/InsightFace processing is the real engine, not a mock implementation.
- Model weights are not committed to Git or bundled into the APK.
- A GPU-capable worker is required for useful real-time inference.
- No fake virtual-camera registration or success state is allowed.
- Stock Android system-wide virtual-camera support is not promised for ordinary APKs.
- Every implementation task must have a focused test before its implementation.

---

### Task 1: Establish backend service skeleton and model boundary

**Files:**
- Create: `backend/README.md`
- Create: `backend/requirements.txt`
- Create: `backend/Dockerfile`
- Create: `backend/app/__init__.py`
- Create: `backend/app/config.py`
- Create: `backend/app/main.py`
- Create: `backend/app/models.py`
- Test: `backend/tests/test_health.py`

**Interfaces:**
- Produces `GET /health` returning `{ "status": "ok", "model_ready": <bool> }`.
- Produces `POST /v1/sessions` returning a session identifier and opaque session token.
- Produces `POST /v1/sessions/{session_id}/reference` accepting a multipart image.
- `ModelRuntime` exposes `load()`, `ready`, `set_reference(image_bytes)`, and `process(frame_bgr)`.

- [ ] **Step 1: Write the failing health test**

```python
def test_health_reports_service_ready(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "model_ready" in response.json()
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `cd backend && pytest tests/test_health.py -v`
Expected: FAIL because the FastAPI application does not exist yet.

- [ ] **Step 3: Implement configuration, app, session model, and health endpoint**

Use environment variables `KEMZY_MODEL_DIR`, `KEMZY_SESSION_TTL_SECONDS`, and `KEMZY_MAX_SESSIONS`. Keep model loading behind `ModelRuntime`; do not import model weights into the Android project.

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd backend && pytest tests/test_health.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend docs/superpowers/specs/2026-09-09-remote-live-backend-design.md
git commit -m "feat: add remote live backend skeleton"
```

---

### Task 2: Integrate the actual Deep-Live-Cam processing boundary

**Files:**
- Create: `backend/app/deep_live_cam_runtime.py`
- Modify: `backend/app/models.py`
- Modify: `backend/app/config.py`
- Test: `backend/tests/test_model_runtime.py`

**Interfaces:**
- `DeepLiveCamRuntime.load() -> None`.
- `DeepLiveCamRuntime.ready -> bool`.
- `DeepLiveCamRuntime.set_reference(image_bytes: bytes) -> None`.
- `DeepLiveCamRuntime.process(frame_bgr: numpy.ndarray) -> numpy.ndarray`.

- [ ] **Step 1: Write tests using a fake processor adapter**

```python
def test_runtime_rejects_processing_before_reference(runtime):
    with pytest.raises(RuntimeError, match="reference"):
        runtime.process(test_frame())


def test_runtime_returns_same_shape_after_processing(runtime_with_reference):
    output = runtime_with_reference.process(test_frame())
    assert output.shape == test_frame().shape
```

- [ ] **Step 2: Run tests and verify the runtime boundary fails**

Run: `cd backend && pytest tests/test_model_runtime.py -v`
Expected: FAIL because the runtime adapter is not implemented.

- [ ] **Step 3: Implement the adapter against the upstream Deep-Live-Cam processing modules**

Vendor only source modules permitted by the upstream project license or install the project as a backend dependency. Resolve the face analyser/swapper/restorer through a single adapter. Configure ONNX Runtime GPU when available and fail explicitly when required model files are absent. Do not silently substitute a fake transform.

- [ ] **Step 4: Run unit tests with the fake processor and a separate model-import smoke test**

Run: `cd backend && pytest tests/test_model_runtime.py -v`
Expected: PASS for boundary tests.

Run: `cd backend && python -c "from app.deep_live_cam_runtime import DeepLiveCamRuntime; print('import ok')"`
Expected: `import ok` when backend dependencies are installed.

- [ ] **Step 5: Commit**

```bash
git add backend/app backend/tests/test_model_runtime.py
git commit -m "feat: connect backend runtime to deep-live-cam"
```

---

### Task 3: Add authenticated session and reference-image lifecycle

**Files:**
- Create: `backend/app/sessions.py`
- Modify: `backend/app/main.py`
- Modify: `backend/app/models.py`
- Test: `backend/tests/test_sessions.py`

**Interfaces:**
- `SessionStore.create() -> Session`.
- `SessionStore.get(session_id, token) -> Session`.
- `SessionStore.delete(session_id, token) -> None`.
- `POST /v1/sessions`.
- `POST /v1/sessions/{session_id}/reference`.
- `DELETE /v1/sessions/{session_id}`.

- [ ] **Step 1: Write failing lifecycle tests**

```python
def test_session_requires_token(client):
    created = client.post("/v1/sessions").json()
    response = client.delete(f"/v1/sessions/{created['session_id']}")
    assert response.status_code == 401


def test_reference_image_is_attached_to_session(client, image_file):
    created = client.post("/v1/sessions").json()
    response = client.post(
        f"/v1/sessions/{created['session_id']}/reference",
        headers={"Authorization": f"Bearer {created['token']}"},
        files={"file": ("reference.jpg", image_file, "image/jpeg")},
    )
    assert response.status_code == 200
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && pytest tests/test_sessions.py -v`
Expected: FAIL because session endpoints do not exist.

- [ ] **Step 3: Implement session storage with TTL and bearer-token validation**

Keep image bytes in memory for the initial implementation and erase them when the session ends or expires. Validate MIME type and decode the image before passing it to the runtime.

- [ ] **Step 4: Run tests**

Run: `cd backend && pytest tests/test_sessions.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/app backend/tests/test_sessions.py
git commit -m "feat: add remote live session lifecycle"
```

---

### Task 4: Implement WebRTC media transport and processed track

**Files:**
- Create: `backend/app/webrtc.py`
- Modify: `backend/app/main.py`
- Test: `backend/tests/test_webrtc.py`

**Interfaces:**
- `POST /v1/sessions/{session_id}/offer` accepts an SDP offer and returns an SDP answer.
- `RemoteVideoTrack` consumes incoming frames, calls `ModelRuntime.process(frame_bgr)`, and emits processed frames.

- [ ] **Step 1: Write failing media-track tests**

```python
@pytest.mark.asyncio
async def test_remote_track_emits_processed_frame(fake_runtime, source_track):
    track = RemoteVideoTrack(source_track, fake_runtime)
    frame = await track.recv()
    assert frame.width == 640
    assert frame.height == 480
    assert fake_runtime.process_calls == 1
```

- [ ] **Step 2: Run the focused test**

Run: `cd backend && pytest tests/test_webrtc.py -v`
Expected: FAIL because the WebRTC track does not exist.

- [ ] **Step 3: Implement aiortc peer connection and video transform**

Accept only authenticated session offers. Attach the incoming video track to the transform. Return the processed video track. Close peer connections on disconnect and session expiry. Do not save incoming frames to disk.

- [ ] **Step 4: Run tests**

Run: `cd backend && pytest tests/test_webrtc.py -v`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/app/webrtc.py backend/app/main.py backend/tests/test_webrtc.py
 git commit -m "feat: add websocket-free webrtc media pipeline"
```

---

### Task 5: Add Android WebRTC client and remote state machine

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/kemzy/liveavatar/RemoteLiveStreamClient.kt`
- Create: `app/src/main/java/com/kemzy/liveavatar/RemoteLiveStreamState.kt`
- Create: `app/src/test/java/com/kemzy/liveavatar/RemoteLiveStreamStateTest.kt`

**Interfaces:**
- `RemoteLiveStreamClient.start(baseUrl: String, reference: ByteArray)`.
- `RemoteLiveStreamClient.stop()`.
- `RemoteLiveStreamClient.state: RemoteLiveStreamState`.
- `RemoteLiveStreamClient.onProcessedVideoSurface(surface: Surface)`.

- [ ] **Step 1: Write failing state-machine tests**

```kotlin
@Test
fun processedReadiness_requires_received_remote_frame() {
    val state = RemoteLiveStreamStateMachine()
    state.connected()
    assertFalse(state.isProcessedReady)
    state.receivedProcessedFrame()
    assertTrue(state.isProcessedReady)
}
```

- [ ] **Step 2: Run the focused Android unit test**

Run: `./gradlew :app:testDebugUnitTest --tests '*RemoteLiveStreamStateTest'`
Expected: FAIL because the state machine does not exist.

- [ ] **Step 3: Add the Android WebRTC dependency and implement signaling/media lifecycle**

Use HTTPS for session/reference requests and WebRTC for media. Keep the base URL configurable rather than hard-coded to localhost. Expose the remote video track through a `SurfaceViewRenderer`-compatible sink.

- [ ] **Step 4: Run the focused test**

Run: `./gradlew :app:testDebugUnitTest --tests '*RemoteLiveStreamStateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java app/src/test
 git commit -m "feat: add android remote live stream client"
```

---

### Task 6: Replace local live output with remote processed output

**Files:**
- Modify: `app/src/main/java/com/kemzy/liveavatar/LiveCameraController.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/AvatarStreamingService.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/MainActivity.kt`
- Modify: relevant live-screen layout/resources
- Test: existing live camera/controller tests plus new remote integration tests

**Interfaces:**
- Start Live Swap invokes the remote session, not `OnDeviceFaceSwapEngine`.
- The main live surface renders the returned WebRTC video.
- Background live uses the same `RemoteLiveStreamClient` service-owned lifecycle.

- [ ] **Step 1: Add a failing regression test that local model readiness cannot mark the UI processed-ready**

```kotlin
@Test
fun local_camera_frames_do_not_mean_remote_processing_ready() {
    val state = RemoteLiveStreamStateMachine()
    state.cameraFramesFlowing()
    assertFalse(state.isProcessedReady)
}
```

- [ ] **Step 2: Run the regression test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*RemoteLiveStream*'`
Expected: FAIL until the new state is wired into the live flow.

- [ ] **Step 3: Wire CameraX capture into WebRTC and the returned track into the main preview**

The local preview must be a diagnostic/source surface, while the main live surface displays the processed remote track after the first remote frame. Remove the old service behavior that treats `faceSwapEngine.isReady` as sufficient for live processing. Preserve avatar persistence and the existing start/stop actions.

- [ ] **Step 4: Run Android unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java app/src/test app/src/main/res
 git commit -m "feat: route kemzy live output through remote inference"
```

---

### Task 7: Processed recording and playback integration

**Files:**
- Modify: `app/src/main/java/com/kemzy/liveavatar/AvatarSurfaceRecorder.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/MainActivity.kt`
- Test: recorder-focused tests

**Interfaces:**
- Recording receives the processed remote video surface/frames rather than the unprocessed CameraX feed.
- Existing import/playback behavior remains intact.

- [ ] **Step 1: Write a failing recorder-source test**

```kotlin
@Test
fun recorder_uses_processed_output_source() {
    val recorder = RecorderSourceSelector()
    assertEquals(OutputSource.PROCESSED_REMOTE, recorder.activeSource)
}
```

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :app:testDebugUnitTest --tests '*Recorder*'`
Expected: FAIL until the processed source is wired.

- [ ] **Step 3: Route recording to the processed output**

Ensure recording stops cleanly when the remote session ends and that playback can open the resulting file. Do not record the source camera unless explicitly selected as a future feature.

- [ ] **Step 4: Run the focused test**

Run: `./gradlew :app:testDebugUnitTest --tests '*Recorder*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java app/src/test
 git commit -m "feat: record processed remote avatar output"
```

---

### Task 8: Make virtual-camera integration capability-gated and consume processed frames

**Files:**
- Modify: `app/src/main/java/com/kemzy/liveavatar/VirtualCameraBridge.kt`
- Modify: `app/src/main/java/com/kemzy/liveavatar/VirtualCameraCapability.kt`
- Modify: existing virtual-camera tests
- Test: `app/src/test/java/com/kemzy/liveavatar/VirtualCameraFrameBridgeTest.kt`

**Interfaces:**
- `VirtualCameraBridge` may enter `REGISTERED` only after actual platform registration succeeds.
- Virtual-camera frames are sourced from the processed remote output bus.
- Unsupported stock Android remains an explicit `FAILED`/unsupported state.

- [ ] **Step 1: Add a failing test ensuring an unprocessed frame cannot be published**

```kotlin
@Test
fun virtual_camera_rejects_source_camera_frame_when_processed_frame_is_missing() {
    val bridge = TestVirtualCameraBridge()
    bridge.clearProcessedFrame()
    assertFalse(bridge.canPublishFrame())
}
```

- [ ] **Step 2: Run virtual-camera tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*VirtualCameraFrameBridgeTest'`
Expected: FAIL until source gating is updated.

- [ ] **Step 3: Update the bridge to consume only fresh processed frames and preserve capability checks**

Do not bypass Android permissions with hidden APIs as a way to claim stock-device support. Keep the real VirtualDevice/VirtualCamera route behind the capability probe and report the actual platform error when registration is denied.

- [ ] **Step 4: Run the full Android test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java app/src/test
 git commit -m "fix: gate virtual camera on real processed output"
```

---

### Task 9: Containerize and prepare GPU deployment

**Files:**
- Modify: `backend/Dockerfile`
- Create: `backend/docker-compose.gpu.yml`
- Create: `backend/.dockerignore`
- Modify: `backend/README.md`
- Create: `.github/workflows/backend.yml`

**Interfaces:**
- Container starts FastAPI on port 8080.
- `/health` reports model readiness.
- Environment variables configure model location and GPU provider.

- [ ] **Step 1: Add deployment smoke test**

```bash
cd backend
python -m compileall app
```

Expected before container work: backend modules compile successfully.

- [ ] **Step 2: Build the container**

Run: `docker build -t kemzy-live-backend ./backend`
Expected: image builds without embedding model credentials or model files.

- [ ] **Step 3: Add GPU runtime configuration**

Configure NVIDIA container runtime and `onnxruntime-gpu` without assuming a specific provider. Keep signaling/control compatible with ordinary HTTPS hosting, but document that inference requires GPU hardware for useful real-time performance.

- [ ] **Step 4: Run container health check**

Run: `docker run --rm -p 8080:8080 kemzy-live-backend`
Expected: `GET /health` responds successfully; `model_ready` reflects actual model availability.

- [ ] **Step 5: Commit**

```bash
git add backend .github/workflows/backend.yml
 git commit -m "chore: containerize gpu live backend"
```

---

### Task 10: End-to-end Internet verification and release build

**Files:**
- Modify: `backend/README.md`
- Modify: `docs/superpowers/specs/2026-09-09-remote-live-backend-design.md` if verification details need recording

- [ ] **Step 1: Deploy backend to a public HTTPS GPU endpoint**

Use a GPU-capable provider. If Render or Railway is used, use it for control/signaling only unless the selected service actually provides the required GPU inference capability. Record the resulting HTTPS endpoint in local deployment configuration, never in source as a secret.

- [ ] **Step 2: Verify health and model readiness remotely**

Run:

```bash
curl -fsS https://<deployed-host>/health
```

Expected: HTTP 200 and `status` equal to `ok`; `model_ready` must be `true` before live processing is attempted.

- [ ] **Step 3: Configure Android with the HTTPS backend endpoint**

Set the endpoint through the app's non-secret build/runtime configuration. The production app must not default to `127.0.0.1`, `localhost`, or a Termux LAN address.

- [ ] **Step 4: Run Android tests and build**

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected: all tests pass and the APK assembles successfully.

- [ ] **Step 5: Perform real-device end-to-end test**

On the Android device, select a reference image, start Live Swap, confirm the app reports backend connection and processed-frame readiness, and confirm the returned avatar video visibly changes with the camera. Confirm stopping tears down the remote session.

- [ ] **Step 6: Verify virtual-camera behavior honestly**

On the target phone, confirm the capability result. If the OS grants the VirtualCamera API, verify a registered camera actually receives the processed frames. If the OS denies registration, verify Kemzy reports unsupported/denied rather than showing a fake camera.

- [ ] **Step 7: Commit verification documentation**

```bash
git add docs backend/README.md
 git commit -m "docs: record remote live verification procedure"
```
