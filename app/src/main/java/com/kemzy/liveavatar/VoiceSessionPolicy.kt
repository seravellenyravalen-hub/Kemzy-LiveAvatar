package com.kemzy.liveavatar

/** Immutable voice-source selection while Live is active. */
class VoiceSessionPolicy {
    var selected: VoiceSourceType? = null
        private set
    var isStreaming: Boolean = false
        private set

    fun select(source: VoiceSourceType): Boolean {
        if (isStreaming && selected != source) return false
        selected = source
        return true
    }

    fun beginStreaming(): Boolean {
        if (selected == null || isStreaming) return false
        isStreaming = true
        return true
    }

    fun stopStreaming() {
        isStreaming = false
        selected = null
    }

    fun isLockedTo(source: VoiceSourceType): Boolean = isStreaming && selected == source
}
