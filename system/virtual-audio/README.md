# Kémzy virtual microphone — AOSP integration boundary

This directory is intentionally **not** part of the ordinary Android APK. A true system-wide microphone requires a compatible AOSP/custom system image with privileged system integration.

## Target architecture

`Kémzy app PCM -> authenticated system transport -> virtual Audio HAL input stream -> audio policy -> AudioFlinger -> normal microphone clients`

For Android 14+ AOSP, new Audio HAL implementations use stable AIDL. The implementation belongs on the system/vendor side, not in `app/src/main`. The HAL must expose a virtual input module/stream with an explicit format contract (recommended baseline: PCM 16-bit, 48 kHz, mono) and bounded buffering.

## Required pieces

1. A privileged bridge service accepts PCM only from the signed Kémzy application/UID.
2. The bridge uses a bounded shared-memory/ring-buffer transport and an authenticated Binder control channel.
3. The Audio HAL exposes a virtual input device/port and reads from that transport.
4. Audio policy describes the device so framework clients can discover/select it normally.
5. SELinux policy restricts bridge and HAL access to the intended system domains.
6. Disconnect/timeout fails closed to silence; it must never fall back to the physical microphone.
7. User-visible Android microphone privacy indicators and foreground-service requirements remain intact.

## APK boundary

`VirtualMicrophoneBridge` in the app is deliberately fail-closed on stock Android. It must never claim to be a global microphone unless a real system bridge is connected.

## Validation

The system image must be tested with at least one ordinary microphone client (for example, a video-call app) and verified that the selected Kémzy input receives only the current session's PCM, stops on explicit Stop, and becomes unavailable after bridge authentication is lost.
