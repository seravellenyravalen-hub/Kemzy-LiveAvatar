package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceTrackingResultTest {
    @Test
    fun noFaceIsRepresentedByZeroFaces() {
        assertEquals(0, FaceTrackingResult.none().faceCount)
    }

    @Test
    fun oneFaceCarriesHeadPoseAndBounds() {
        val result = FaceTrackingResult(
            faceCount = 1,
            yawDegrees = 12f,
            pitchDegrees = -4f,
            rollDegrees = 3f,
            centerX = 250f,
            centerY = 400f,
            width = 300f,
            height = 360f
        )
        assertEquals(1, result.faceCount)
        assertEquals(12f, result.yawDegrees)
        assertEquals(-4f, result.pitchDegrees)
        assertEquals(3f, result.rollDegrees)
        assertEquals(250f, result.centerX)
        assertEquals(400f, result.centerY)
        assertEquals(300f, result.width)
        assertEquals(360f, result.height)
    }
}
