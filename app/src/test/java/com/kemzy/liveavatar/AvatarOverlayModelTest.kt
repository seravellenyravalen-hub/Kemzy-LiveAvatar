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
    fun trackedFaceProducesCenteredOverlay() {
        val result = FaceTrackingResult(faceCount = 1, yawDegrees = 10f, pitchDegrees = -5f, rollDegrees = 20f)
        val model = AvatarOverlayModel.from(result, 1000, 1000)
        assertEquals(true, model.visible)
        assertEquals(500f, model.centerX)
        assertEquals(500f, model.centerY)
        assertEquals(20f, model.rotationDegrees)
    }
}
