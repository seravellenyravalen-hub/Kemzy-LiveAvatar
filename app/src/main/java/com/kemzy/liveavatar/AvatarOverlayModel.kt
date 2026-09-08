package com.kemzy.liveavatar

data class AvatarOverlayModel(
    val visible: Boolean,
    val centerX: Float,
    val centerY: Float,
    val rotationDegrees: Float
) {
    companion object {
        fun from(result: FaceTrackingResult, width: Int, height: Int): AvatarOverlayModel {
            if (result.faceCount <= 0 || width <= 0 || height <= 0) {
                return AvatarOverlayModel(false, 0f, 0f, 0f)
            }
            return AvatarOverlayModel(
                visible = true,
                centerX = width / 2f,
                centerY = height / 2f,
                rotationDegrees = result.rollDegrees
            )
        }
    }
}
