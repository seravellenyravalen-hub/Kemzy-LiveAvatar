package com.kemzy.liveavatar

/**
 * Process-independent policy for the live session: leaving the Activity must
 * never unlock or replace the selected reference. Only an explicit stop does.
 */
class CameraSessionPolicy {
    private var reference: String? = null

    var isRunning: Boolean = false
        private set

    var isReferenceLocked: Boolean = false
        private set

    var shouldReleaseCamera: Boolean = false
        private set

    fun start(referenceUri: String) {
        require(referenceUri.isNotBlank())
        check(!isRunning) { "Camera session already running" }
        reference = referenceUri
        isRunning = true
        isReferenceLocked = true
        shouldReleaseCamera = false
    }

    fun onActivityHidden() {
        if (isRunning) shouldReleaseCamera = false
    }

    fun onNetworkChanged() {
        // Deliberate no-op: network state must never mutate the locked reference.
    }

    fun isReferenceLockedTo(uri: String): Boolean =
        isReferenceLocked && reference == uri

    fun stop() {
        isRunning = false
        isReferenceLocked = false
        reference = null
        shouldReleaseCamera = true
    }
}
