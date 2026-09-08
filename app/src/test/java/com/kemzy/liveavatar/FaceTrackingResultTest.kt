package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceTrackingResultTest {
    @Test
    fun noFaceIsRepresentedByZeroFaces() {
        assertEquals(0, FaceTrackingResult.none().faceCount)
    }

    @Test
    fun oneFaceCarriesHeadPoseAndNormalizedBounds() {
        val result = FaceTrackingResult(
            faceCount = 1,
            yawDegrees = 12f,
            pitchDegrees = -4f,
            rollDegrees = 3f,
            centerX = 0.25f,
            centerY = 0.4f,
            width = 0.3f,
            height = 0.36f
        )
        assertEquals(1, result.faceCount)
        assertEquals(12f, result.yawDegrees)
        assertEquals(-4f, result.pitchDegrees)
        assertEquals(3f, result.rollDegrees)
        assertEquals(0.25f, result.centerX)
        assertEquals(0.4f, result.centerY)
        assertEquals(0.3f, result.width)
        assertEquals(0.36f, result.height)
    }
}
