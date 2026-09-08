package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceFaceSwapEngineTest {
    @Test
    fun missing_required_models_keeps_engine_in_fallback() {
        val engine = OnDeviceFaceSwapEngine(
            missingModels = listOf("face-detector", "arcface-embedder", "face-swapper")
        )

        engine.setAvatar("content://avatar")
        val state = engine.prepareAvatar()

        assertFalse(state.isNeuralReady)
        assertEquals(LiveFaceEngineState.Fallback("Missing models: face-detector, arcface-embedder, face-swapper"), state)
    }

    @Test
    fun frame_never_claims_neural_output_before_ready() {
        val engine = OnDeviceFaceSwapEngine(missingModels = listOf("face-swapper"))
        engine.setAvatar("content://avatar")
        engine.prepareAvatar()

        val frame = engine.processFrame(FaceTrackingResult.none())
        assertFalse(frame.isNeural)
        assertTrue(frame.message!!.contains("face-swapper"))
    }
}
