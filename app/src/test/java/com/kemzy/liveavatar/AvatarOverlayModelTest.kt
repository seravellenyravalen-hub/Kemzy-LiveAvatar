package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarOverlayModelTest {
    @Test
    fun noFaceProducesHiddenOverlay() {
        val model = AvatarOverlayModel.from(FaceTrackingResult.none(), 1000, 1000)
        assertEquals(false, model.visible)
    }

    @Test
    fun trackedFaceUsesNormalizedBoundsAndRotation() {
        val result = FaceTrackingResult(
            faceCount = 1,
            centerX = 0.25f,
            centerY = 0.40f,
            width = 0.30f,
            height = 0.36f,
            rollDegrees = 20f
        )
        val model = AvatarOverlayModel.from(result, 1000, 1000)
        assertEquals(true, model.visible)
        assertEquals(250f, model.centerX)
        assertEquals(400f, model.centerY)
        assertEquals(300f, model.width)
        assertEquals(360f, model.height)
        assertEquals(20f, model.rotationDegrees)
    }
}
