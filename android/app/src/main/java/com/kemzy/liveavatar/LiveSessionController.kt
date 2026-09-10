package com.kemzy.liveavatar

/** Coordinates the desktop-style Live button with the Android camera pipeline. */
class LiveSessionController(
    private val state: LiveSessionState
) {
    val isRunning: Boolean
        get() = state.isLive

    fun start() {
        state.startLive()
    }

    fun stop() {
        state.stopLive()
    }
}
