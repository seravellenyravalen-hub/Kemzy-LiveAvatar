package com.kemzy.liveavatar

data class FaceSwapFrame(
    val isNeural: Boolean,
    val bitmap: android.graphics.Bitmap? = null,
    val message: String? = null
) {
    companion object {
        fun neural(bitmap: android.graphics.Bitmap?) = FaceSwapFrame(
            isNeural = true,
            bitmap = bitmap
        )

        fun fallback(message: String) = FaceSwapFrame(
            isNeural = false,
            message = message
        )
    }
}

sealed interface LiveFaceEngineState {
    val isNeuralReady: Boolean
        get() = false

    data object Idle : LiveFaceEngineState
    data object Preparing : LiveFaceEngineState
    data object Ready : LiveFaceEngineState {
        override val isNeuralReady: Boolean = true
    }
    data class Fallback(val reason: String) : LiveFaceEngineState
}
