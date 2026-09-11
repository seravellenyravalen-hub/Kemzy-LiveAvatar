package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceTensorPreprocessorTest {
    @Test
    fun arcface_normalization_maps_midpoint_to_zero() {
        val output = FaceTensorPreprocessor.arcFace(floatArrayOf(127.5f), 1)
        assertEquals(0f, output[0], 0.0001f)
        assertEquals(0f, output[1], 0.0001f)
        assertEquals(0f, output[2], 0.0001f)
    }

    @Test
    fun swapper_normalization_maps_white_to_one() {
        val output = FaceTensorPreprocessor.swapper(floatArrayOf(255f), 1)
        assertEquals(1f, output[0], 0.0001f)
        assertEquals(1f, output[1], 0.0001f)
        assertEquals(1f, output[2], 0.0001f)
    }
}
