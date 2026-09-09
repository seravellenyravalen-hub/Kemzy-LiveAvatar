package com.kemzy.liveavatar

enum class RemoteLiveStreamState {
    IDLE,
    CONNECTING,
    CONNECTED,
    PROCESSING,
    FAILED,
    STOPPED
}

class RemoteLiveStreamStateMachine {
    var state: RemoteLiveStreamState = RemoteLiveStreamState.IDLE
        private set

    var isProcessedReady: Boolean = false
        private set

    fun connecting() {
        state = RemoteLiveStreamState.CONNECTING
        isProcessedReady = false
    }

    fun connected() {
        state = RemoteLiveStreamState.CONNECTED
        isProcessedReady = false
    }

    fun cameraFramesFlowing() {
        if (state == RemoteLiveStreamState.IDLE) {
            state = RemoteLiveStreamState.CONNECTING
        }
    }

    fun receivedProcessedFrame() {
        state = RemoteLiveStreamState.PROCESSING
        isProcessedReady = true
    }

    fun failed() {
        state = RemoteLiveStreamState.FAILED
        isProcessedReady = false
    }

    fun stopped() {
        state = RemoteLiveStreamState.STOPPED
        isProcessedReady = false
    }
}
