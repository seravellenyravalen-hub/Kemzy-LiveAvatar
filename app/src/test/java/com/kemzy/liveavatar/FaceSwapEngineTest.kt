package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceSwapEngineTest {
    @Test
    fun engineStartsUninitialized() {
        val engine = PreviewFaceSwapEngine()
        assertEquals(false, engine.isReady)
    }

    @Test
    fun previewEngineRemainsFallbackAfterAvatarSelection() {
        val engine = PreviewFaceSwapEngine()
        engine.setAvatar("content://avatar")
        assertEquals(false, engine.isReady)
        assertEquals("content://avatar", engine.avatarUri)
        assertEquals(
            LiveFaceEngineState.Fallback("Neural face-swap models are not installed"),
            engine.prepareAvatar()
        )
    }

    @Test
    fun clearingAvatarResetsEngine() {
        val engine = PreviewFaceSwapEngine()
        engine.setAvatar("content://avatar")
        engine.clearAvatar()
        assertEquals(false, engine.isReady)
    }
}
