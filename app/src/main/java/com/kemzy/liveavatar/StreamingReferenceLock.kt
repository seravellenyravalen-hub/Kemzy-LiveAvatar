package com.kemzy.liveavatar

/**
 * Owns the reference image used by a live session.
 *
 * Once streaming begins, the selected reference is immutable until the session stops.
 * Network events intentionally have no effect on the locked reference.
 */
class StreamingReferenceLock {
    var selected: String? = null
        private set

    var isStreaming: Boolean = false
        private set

    fun select(uri: String): Boolean {
        if (isStreaming) return false
        if (uri.isBlank()) return false
        selected = uri
        return true
    }

    fun beginStreaming(): Boolean {
        if (selected == null || isStreaming) return false
        isStreaming = true
        return true
    }

    fun stopStreaming() {
        isStreaming = false
    }

    fun onNetworkChanged() {
        // Deliberately a no-op. The active stream owns its reference locally.
    }
}
