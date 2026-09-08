package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceSwapGeometryTest {
    @Test
    fun targetCropUsesTrackedFaceAndAddsMargin() {
        val crop = FaceSwapGeometry.targetCrop(
            tracking = FaceTrackingResult(
                faceCount = 1,
                centerX = 0.5f,
                centerY = 0.5f,
                width = 0.2f,
                height = 0.3f
            ),
            imageWidth = 1000,
            imageHeight = 800,
            margin = 0.35f
        )

        assertEquals(380, crop.left)
        assertEquals(280, crop.top)
        assertEquals(240, crop.width)
        assertEquals(240, crop.height)
    }

    @Test
    fun cropIsClampedToImageBounds() {
        val crop = FaceSwapGeometry.targetCrop(
            tracking = FaceTrackingResult(
                faceCount = 1,
                centerX = 0.04f,
                centerY = 0.06f,
                width = 0.2f,
                height = 0.2f
            ),
            imageWidth = 1000,
            imageHeight = 800,
            margin = 0.5f
        )

        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(300, crop.width)
        assertEquals(300, crop.height)
    }
}
