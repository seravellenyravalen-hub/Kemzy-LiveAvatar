package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveModeSettingsTest {
    @Test
    fun defaultsMatchDeepLiveCamLiveControls() {
        val settings = LiveModeSettings()

        assertTrue(settings.liveMirror)
        assertTrue(settings.liveResizable)
        assertTrue(settings.mouthMask)
        assertEquals(false, settings.manyFaces)
        assertEquals(false, settings.faceMapping)
    }

    @Test
    fun sourceFaceMustBeSelectedBeforeLiveStarts() {
        val state = LiveSessionState()

        assertEquals(false, state.canStartLive)
        state.selectSource("/data/source-face.jpg")
        assertEquals(true, state.canStartLive)
        state.stopLive()
        assertEquals(false, state.isLive)
    }
}
