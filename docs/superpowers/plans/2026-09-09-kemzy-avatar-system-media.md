# Kémzy àvátâr — System Media Hardening Plan

## Goal
Finish the approved Kémzy àvátâr architecture on branch `kemzy-avatar-system-media`: harden the live avatar session, add locked PCM voice plumbing and capability-aware system media bridges, improve branding/UI state handling, add AOSP integration boundaries, and verify the Android project with tests/build.

## Tasks
1. Branding and manifest: rename launcher/notification strings to Kémzy àvátâr, replace the old icon with a clean vector brand mark, and declare microphone foreground-service capability for background voice capture.
2. Session reliability: add a single session coordinator/policy for immutable avatar and voice sources, idempotent teardown, lifecycle/network invariance, and safe restart behavior.
3. Camera pipeline: make model preparation retryable, serialize inference, bound frame flow, never publish the user's unprocessed camera frame, and recover camera binding with bounded backoff.
4. Voice pipeline: add PCM16 voice source abstraction, locked voice source policy, bounded PCM ring buffer, live AudioRecord source, and imported-file source plumbing without pretending it is a system microphone.
5. System media boundaries: add capability-aware virtual camera and virtual microphone bridges; stock Android reports unsupported rather than faking system-wide devices. Add AOSP-side documentation and reference integration skeleton for compatible system images.
6. UI/UX: expose only useful readiness/live/stopping states, keep the selected image locked during Live, preserve the active session across Activity recreation/backgrounding, and avoid raw diagnostics or raw-face fallback.
7. Tests: add unit tests for source locking, ring-buffer bounds, bridge state transitions, lifecycle/network invariance, and teardown idempotency.
8. Verification: run Gradle unit tests and debug assembly through GitHub Actions, inspect failures, fix them, rerun, and only report completion with fresh passing evidence.

## Platform boundary
A normal APK must not claim it can globally replace another app's camera/microphone on arbitrary stock Android. The app layer must capability-detect. Compatible AOSP/custom system images require a privileged/system virtual-camera path and an AIDL Audio HAL/policy integration for a true system-wide virtual microphone.
