package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFaceEngineStateTest {
    @Test
    fun fallback_is_not_neural() {
        val state = LiveFaceEngineState.Fallback("models missing")
        assertFalse(state.isNeuralReady)
        assertEquals("models missing", state.reason)
    }

    @Test
    fun ready_is_neural() {
        val state = LiveFaceEngineState.Ready
        assertTrue(state.isNeuralReady)
    }

    @Test
    fun frame_distinguishes_neural_from_fallback() {
        val neural = FaceSwapFrame.neural(null)
        val fallback = FaceSwapFrame.fallback("models missing")

        assertTrue(neural.isNeural)
        assertFalse(fallback.isNeural)
        assertEquals("models missing", fallback.message)
    }
}
