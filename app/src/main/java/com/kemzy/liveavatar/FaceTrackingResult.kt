package com.kemzy.liveavatar

import android.graphics.PointF

data class FaceTrackingResult(
    val faceCount: Int,
    val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val smilingProbability: Float? = null,
    val landmarks: List<PointF> = emptyList()
) {
    companion object {
        fun none() = FaceTrackingResult(faceCount = 0)
    }
}
