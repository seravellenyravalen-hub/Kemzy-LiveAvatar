package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamingContractTest {
    @Test
    fun neural_frame_is_the_only_frame_allowed_for_live_output() {
        val neural = FaceSwapFrame.neural(null)
        val fallback = FaceSwapFrame.fallback("hidden")

        assertTrue(neural.isNeural)
        assertFalse(fallback.isNeural)
    }

    @Test
    fun tracking_result_contains_expression_signals_used_by_live_pipeline() {
        val tracking = FaceTrackingResult(
            faceCount = 1,
            yawDegrees = 12f,
            pitchDegrees = -4f,
            rollDegrees = 3f,
            centerX = .5f,
            centerY = .5f,
            width = .3f,
            height = .4f,
            leftEyeOpenProbability = .9f,
            rightEyeOpenProbability = .85f,
            smilingProbability = .7f
        )

        assertTrue(tracking.leftEyeOpenProbability!! > 0f)
        assertTrue(tracking.rightEyeOpenProbability!! > 0f)
        assertTrue(tracking.smilingProbability!! > 0f)
    }
}
