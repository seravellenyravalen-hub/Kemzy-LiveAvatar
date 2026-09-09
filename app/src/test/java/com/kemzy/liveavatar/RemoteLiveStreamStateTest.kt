package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLiveStreamStateTest {
    @Test
    fun processedReadiness_requires_received_remote_frame() {
        val state = RemoteLiveStreamStateMachine()
        state.connected()
        assertFalse(state.isProcessedReady)
        state.receivedProcessedFrame()
        assertTrue(state.isProcessedReady)
    }

    @Test
    fun local_camera_frames_do_not_mean_remote_processing_ready() {
        val state = RemoteLiveStreamStateMachine()
        state.cameraFramesFlowing()
        assertFalse(state.isProcessedReady)
    }
}
