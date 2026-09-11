package com.kemzy.liveavatar

import android.graphics.Bitmap

interface FaceSwapEngine {
    val avatarUri: String?
    val state: LiveFaceEngineState
    val isReady: Boolean
        get() = state.isNeuralReady

    fun setAvatar(uri: String)
    fun prepareAvatar(): LiveFaceEngineState
    fun processFrame(tracking: FaceTrackingResult): FaceSwapFrame

    /** Real camera-frame path. Implementations may override this when neural inference is ready. */
    fun processFrame(frame: Bitmap, tracking: FaceTrackingResult): FaceSwapFrame =
        processFrame(tracking)

    fun clearAvatar()
    fun close()
}

/**
 * Explicit fallback implementation. It keeps the existing tracking UI working while
 * the neural ONNX backend is unavailable. It never labels a tracking overlay as a swap.
 */
class PreviewFaceSwapEngine : FaceSwapEngine {
    override var avatarUri: String? = null
        private set

    override var state: LiveFaceEngineState = LiveFaceEngineState.Idle
        private set

    override fun setAvatar(uri: String) {
        avatarUri = uri
        state = LiveFaceEngineState.Preparing
    }

    override fun prepareAvatar(): LiveFaceEngineState {
        state = if (avatarUri == null) {
            LiveFaceEngineState.Fallback("No avatar selected")
        } else {
            LiveFaceEngineState.Fallback("Neural face-swap models are not installed")
        }
        return state
    }

    override fun processFrame(tracking: FaceTrackingResult): FaceSwapFrame =
        FaceSwapFrame.fallback(
            (state as? LiveFaceEngineState.Fallback)?.reason
                ?: "Neural face-swap engine is not ready"
        )

    override fun clearAvatar() {
        avatarUri = null
        state = LiveFaceEngineState.Idle
    }

    override fun close() = Unit
}
