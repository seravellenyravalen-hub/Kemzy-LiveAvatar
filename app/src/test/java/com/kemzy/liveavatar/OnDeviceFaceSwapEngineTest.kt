package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceFaceSwapEngineTest {
    @Test
    fun missing_required_models_never_reports_ready() {
        val engine = OnDeviceFaceSwapEngine(
            missingModels = listOf("arcface-embedder", "face-swapper", "inswapper-emap")
        )

        engine.setAvatar("content://avatar")
        val state = engine.prepareAvatar()

        assertFalse(state.isNeuralReady)
        assertEquals(LiveFaceEngineState.Fallback("Required runtime models are unavailable"), state)
    }

    @Test
    fun frame_never_claims_neural_output_before_ready() {
        val engine = OnDeviceFaceSwapEngine(missingModels = listOf("face-swapper"))
        engine.setAvatar("content://avatar")
        engine.prepareAvatar()

        val frame = engine.processFrame(FaceTrackingResult.none())
        assertFalse(frame.isNeural)
    }
}
