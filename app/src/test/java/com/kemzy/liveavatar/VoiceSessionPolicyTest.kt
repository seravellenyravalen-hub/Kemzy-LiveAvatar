package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionPolicyTest {
    @Test
    fun sourceCannotChangeWhileStreaming() {
        val policy = VoiceSessionPolicy()
        assertTrue(policy.select(VoiceSourceType.MICROPHONE))
        assertTrue(policy.beginStreaming())
        assertFalse(policy.select(VoiceSourceType.IMPORTED_FILE))
        assertTrue(policy.isLockedTo(VoiceSourceType.MICROPHONE))
    }

    @Test
    fun stopUnlocksSource() {
        val policy = VoiceSessionPolicy()
        policy.select(VoiceSourceType.IMPORTED_FILE)
        policy.beginStreaming()
        policy.stopStreaming()
        assertFalse(policy.isStreaming)
        assertTrue(policy.select(VoiceSourceType.MICROPHONE))
    }
}
