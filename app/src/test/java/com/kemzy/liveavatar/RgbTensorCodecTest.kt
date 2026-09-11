package com.kemzy.liveavatar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbTensorCodecTest {
    @Test
    fun arcFaceRgb_uses_channel_first_layout_and_minus_one_to_one() {
        val rgb = floatArrayOf(
            255f, 0f, 0f,
            0f, 255f, 0f
        )

        val output = RgbTensorCodec.arcFace(rgb, width = 2, height = 1)

        assertEquals(6, output.size)
        assertArrayEquals(
            floatArrayOf(1f, -1f, -1f, 1f, -1f, -1f),
            output,
            0.0001f
        )
    }

    @Test
    fun swapperRgb_uses_channel_first_layout_and_zero_to_one() {
        val rgb = floatArrayOf(
            255f, 128f, 0f,
            0f, 64f, 255f
        )

        val output = RgbTensorCodec.swapper(rgb, width = 2, height = 1)

        assertEquals(6, output.size)
        assertArrayEquals(
            floatArrayOf(
                1f,
                0f,
                128f / 255f,
                64f / 255f,
                0f,
                1f
            ),
            output,
            0.0001f
        )
    }
}
