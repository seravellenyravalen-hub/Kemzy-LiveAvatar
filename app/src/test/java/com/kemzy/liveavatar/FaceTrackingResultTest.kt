package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceTrackingResultTest {
    @Test
    fun noFaceIsRepresentedByZeroFaces() {
        assertEquals(0, FaceTrackingResult.none().faceCount)
    }

    @Test
    fun oneFaceCarriesHeadPose() {
        val result = FaceTrackingResult(faceCount = 1, yawDegrees = 12f, pitchDegrees = -4f, rollDegrees = 3f)
        assertEquals(1, result.faceCount)
        assertEquals(12f, result.yawDegrees)
        assertEquals(-4f, result.pitchDegrees)
        assertEquals(3f, result.rollDegrees)
    }
}
