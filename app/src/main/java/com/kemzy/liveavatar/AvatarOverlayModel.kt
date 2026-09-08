package com.kemzy.liveavatar

data class AvatarOverlayModel(
    val visible: Boolean,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float
) {
    companion object {
        fun from(result: FaceTrackingResult, viewWidth: Int, viewHeight: Int): AvatarOverlayModel {
            if (result.faceCount <= 0 || viewWidth <= 0 || viewHeight <= 0 || result.width <= 0f || result.height <= 0f) {
                return AvatarOverlayModel(false, 0f, 0f, 0f, 0f, 0f)
            }
            return AvatarOverlayModel(
                visible = true,
                centerX = result.centerX,
                centerY = result.centerY,
                width = result.width,
                height = result.height,
                rotationDegrees = result.rollDegrees
            )
        }
    }
}
