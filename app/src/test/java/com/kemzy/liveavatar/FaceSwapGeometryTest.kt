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

        assertEquals(296, crop.left)
        assertEquals(196, crop.top)
        assertEquals(408, crop.width)
        assertEquals(408, crop.height)
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
