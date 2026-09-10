package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiveSessionControllerTest {
    @Test
    fun liveCannotStartWithoutSourceFace() {
        val controller = LiveSessionController(LiveSessionState())

        assertFailsWith<IllegalStateException> { controller.start() }
        assertEquals(false, controller.isRunning)
    }

    @Test
    fun liveStartsOnlyAfterSourceFaceSelection() {
        val state = LiveSessionState()
        val controller = LiveSessionController(state)
        state.selectSource("content://source/face")

        controller.start()

        assertEquals(true, controller.isRunning)
        assertEquals(true, state.isLive)
    }

    @Test
    fun stoppingLiveClearsRunningState() {
        val state = LiveSessionState()
        val controller = LiveSessionController(state)
        state.selectSource("content://source/face")
        controller.start()

        controller.stop()

        assertEquals(false, controller.isRunning)
        assertEquals(false, state.isLive)
    }
}
