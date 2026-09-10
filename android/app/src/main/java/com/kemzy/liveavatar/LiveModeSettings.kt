package com.kemzy.liveavatar

/**
 * Android-native representation of the desktop Deep-Live-Cam webcam controls.
 * The values describe behavior; the actual processors are wired separately.
 */
data class LiveModeSettings(
    val liveMirror: Boolean = true,
    val liveResizable: Boolean = true,
    val mouthMask: Boolean = true,
    val manyFaces: Boolean = false,
    val faceMapping: Boolean = false,
    val faceEnhancer: Boolean = false,
    val interpolation: Boolean = false
)

/** Owns the source-face/live state independently from the camera lifecycle. */
class LiveSessionState {
    var sourceFacePath: String? = null
        private set

    var isLive: Boolean = false
        private set

    val canStartLive: Boolean
        get() = !sourceFacePath.isNullOrBlank()

    fun selectSource(path: String) {
        require(path.isNotBlank()) { "Source face path cannot be blank." }
        sourceFacePath = path
        isLive = false
    }

    fun startLive() {
        check(canStartLive) { "Select a source face before starting Live mode." }
        isLive = true
    }

    fun stopLive() {
        isLive = false
    }
}
