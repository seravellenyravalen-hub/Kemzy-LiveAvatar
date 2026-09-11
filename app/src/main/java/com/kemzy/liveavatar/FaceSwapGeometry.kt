package com.kemzy.liveavatar

import kotlin.math.roundToInt

/** Pixel geometry used to crop the tracked target face before neural swapping. */
data class FaceCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

object FaceSwapGeometry {
    fun targetCrop(
        tracking: FaceTrackingResult,
        imageWidth: Int,
        imageHeight: Int,
        margin: Float = 0.35f
    ): FaceCropRect {
        require(imageWidth > 0 && imageHeight > 0)
        require(margin >= 0f)
        require(tracking.faceCount > 0)

        val faceWidth = (tracking.width * imageWidth).coerceAtLeast(1f)
        val faceHeight = (tracking.height * imageHeight).coerceAtLeast(1f)
        val side = (maxOf(faceWidth, faceHeight) * (1f + margin * 2f))
            .roundToInt()
            .coerceAtLeast(1)

        val centerX = tracking.centerX * imageWidth
        val centerY = tracking.centerY * imageHeight
        val left = (centerX - side / 2f).roundToInt().coerceIn(0, imageWidth - 1)
        val top = (centerY - side / 2f).roundToInt().coerceIn(0, imageHeight - 1)
        val right = (left + side).coerceAtMost(imageWidth)
        val bottom = (top + side).coerceAtMost(imageHeight)

        return FaceCropRect(
            left = left,
            top = top,
            width = (right - left).coerceAtLeast(1),
            height = (bottom - top).coerceAtLeast(1)
        )
    }
}
