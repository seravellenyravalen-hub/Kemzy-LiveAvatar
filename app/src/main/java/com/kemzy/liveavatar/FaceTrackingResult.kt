package com.kemzy.liveavatar

data class FaceTrackingResult(
    val faceCount: Int,
    val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    companion object {
        fun none() = FaceTrackingResult(faceCount = 0)
    }
}
