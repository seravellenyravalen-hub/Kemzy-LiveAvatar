# Kemzy LiveAvatar — Android Technical Specification

## Goal
Build a standalone Android APK that uses the phone's own camera to create a local, real-time face-avatar experience: the user selects an image, starts the front camera, the app tracks the user's face/head movement, and a mobile AI face-transformation engine renders the selected avatar in the live preview.

## Scope
- Android-only application.
- Camera is owned and opened by this APK.
- User explicitly starts and stops each live session.
- Avatar source is selected from the device gallery.
- Core processing should work locally on the device where practical.
- No integration with or control of third-party apps is part of the core product.
- Voice is a later subsystem and initially uses the user's own voice rather than cloning another person's voice.

## Architecture
CameraX captures frames. A face-tracking layer estimates landmarks and head pose. A mobile-optimized face-transformation engine consumes the camera frame plus the selected avatar representation. A real-time renderer displays the result in the APK. The AI engine is hidden behind a stable interface so the model can be replaced after benchmarking on the target device.

## Pipeline
`Gallery Image -> Avatar Preparation -> CameraX -> Face Detection/Tracking -> AI Face Engine -> Renderer -> Live Preview`

## Performance Strategy
- Prefer hardware acceleration when supported.
- Provide GPU/NPU acceleration where the selected runtime/device supports it.
- Fall back to CPU.
- Measure FPS, frame latency, memory and thermal behavior on the target phone.
- Adapt processing resolution/quality when necessary instead of assuming a fixed performance level.

## Milestones
1. Android project, permissions, CameraX preview, start/stop lifecycle.
2. Face detection and landmark/head-pose tracking.
3. Gallery avatar selection and local preparation.
4. Mobile AI engine evaluation and integration.
5. Real-time transformation and renderer.
6. Performance optimization and device testing.
7. Optional voice subsystem.

## Acceptance Criteria for First Milestone
- APK launches successfully.
- Camera permission is requested correctly.
- Front camera opens inside the APK.
- Start/Stop controls work.
- Camera resources are released when the session stops.
- Basic FPS/performance telemetry is available.
- Automated unit tests cover session-state logic.

## Safety Boundaries
The application is designed as a user-controlled camera/AI learning project. It must not include features intended to bypass identity verification, defeat security controls, or secretly impersonate another person.
