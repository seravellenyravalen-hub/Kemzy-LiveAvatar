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
    fun engineBecomesReadyAfterAvatarSelection() {
        val engine = PreviewFaceSwapEngine()
        engine.setAvatar("content://avatar")
        assertEquals(true, engine.isReady)
        assertEquals("content://avatar", engine.avatarUri)
    }

    @Test
    fun clearingAvatarResetsEngine() {
        val engine = PreviewFaceSwapEngine()
        engine.setAvatar("content://avatar")
        engine.clearAvatar()
        assertEquals(false, engine.isReady)
    }
}
