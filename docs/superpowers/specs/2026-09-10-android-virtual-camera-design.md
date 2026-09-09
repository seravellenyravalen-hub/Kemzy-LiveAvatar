# Kemzy Android Virtual Camera Design

## Goal
Expose Kemzy's real remote Deep-Live-Cam processed video to Android camera consumers where the device platform actually supports virtual cameras, while never reporting success when Android cannot expose the virtual device/camera to the consumer app.

## Runtime pipeline

Physical phone camera -> Kemzy WebRTC uplink -> remote GPU Deep-Live-Cam/InsightFace runtime -> WebRTC downlink -> Kemzy processed-frame bus -> Android Virtual Camera producer -> Android Camera framework consumer.

WhatsApp/Telegram are consumers only when the Android system exposes the Kemzy virtual camera to them. The backend does not directly become a WhatsApp camera.

## Platform strategy

1. Prefer the official Android VirtualDeviceManager/VirtualCamera APIs on Android 15+ devices that expose and authorize the feature.
2. Keep runtime capability detection separate from camera frame production.
3. Treat system-level/privileged requirements as a hard platform boundary. An ordinary APK must not use hidden APIs or falsely claim system-wide camera registration.
4. If the device cannot expose the virtual camera to normal consumer applications, show an explicit unsupported/privileged-required state and continue to support Kemzy's own processed preview/recording.
5. Do not use MediaProjection as a substitute for a camera device; screen capture is not a camera and does not make WhatsApp select Kemzy as a camera.

## Virtual camera producer

The producer accepts the latest processed frame from the existing frame bus, converts it to the negotiated YUV_420_888 stream, and services camera capture requests. Frames must be copied for independent consumers and must never be recycled while another consumer still owns the copy.

The producer is considered REGISTERED only after Android returns an actual virtual device and virtual camera object. A successful HTTP/WebRTC session alone is never enough to report virtual-camera readiness.

## Capability and status model

Expose these states independently:
- PLATFORM_UNAVAILABLE: API/device support is absent.
- PRIVILEGE_REQUIRED: platform supports the mechanism but this APK cannot register it.
- READY: virtual camera is registered and a consumer can request frames.
- FAILED: registration attempted and failed for another reason.
- STOPPED: no virtual camera is active.

The UI must show which boundary failed. It must not call the virtual camera "working" merely because Kemzy is processing frames.

## WhatsApp/Telegram expectation

On a supported privileged/OEM/system build, the virtual camera should appear through the standard Android camera framework and can then be selected/used by compatible consumer applications. On ordinary stock devices where the virtual camera is restricted to virtual-device contexts or privileged components, Kemzy cannot force another application's camera picker to expose it.

## Security and consent

Only process reference images and camera streams supplied by the user with appropriate rights/consent. Do not add mechanisms intended to bypass consumer-app security controls or conceal synthetic output.

## Testing

- Unit/instrumentation tests verify capability classification and that registration is not reported until a real virtual-camera object exists.
- Frame-bridge tests verify that virtual-camera consumers receive independent frame copies.
- Device verification must test the actual phone build because VirtualDeviceManager support is device/configuration dependent.
