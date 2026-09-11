package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {
    @Test
    fun startThenStop_returnsToIdle() {
        val controller = SessionController()

        assertEquals(SessionState.Idle, controller.state)

        controller.start()
        assertEquals(SessionState.Running, controller.state)

        controller.stop()
        assertEquals(SessionState.Idle, controller.state)
    }
}
