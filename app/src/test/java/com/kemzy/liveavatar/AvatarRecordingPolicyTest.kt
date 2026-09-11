package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRecordingPolicyTest {
    @Test
    fun recordingUsesGeneratedAvatarFramesAndMicrophone() {
        val policy = AvatarRecordingPolicy()
        assertTrue(policy.requiresGeneratedFrames)
        assertTrue(policy.requiresMicrophone)
        assertEquals(30, policy.targetFps)
    }

    @Test
    fun recordingDoesNotUseRawCameraVideoCapture() {
        val policy = AvatarRecordingPolicy()
        assertTrue(policy.usesSurfaceEncoder)
        assertTrue(!policy.usesCameraXVideoCapture)
    }
}
