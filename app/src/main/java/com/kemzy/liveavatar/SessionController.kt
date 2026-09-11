package com.kemzy.liveavatar

sealed interface SessionState {
    data object Idle : SessionState
    data object Running : SessionState
}

class SessionController {
    var state: SessionState = SessionState.Idle
        private set

    fun start() {
        state = SessionState.Running
    }

    fun stop() {
        state = SessionState.Idle
    }
}
