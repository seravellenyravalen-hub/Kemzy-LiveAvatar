package com.kemzy.liveavatar

/** Contract for the recording pipeline used by Kemzy-LiveAvatar. */
class AvatarRecordingPolicy {
    val requiresGeneratedFrames: Boolean = true
    val requiresMicrophone: Boolean = true
    val targetFps: Int = 30
    val usesSurfaceEncoder: Boolean = true
    val usesCameraXVideoCapture: Boolean = false
}
