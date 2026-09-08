package com.kemzy.liveavatar

interface FaceSwapEngine {
    val isReady: Boolean
    val avatarUri: String?

    fun setAvatar(uri: String)
    fun clearAvatar()
    fun close()
}

/**
 * Tracking-only engine used until the device-specific neural face-swap backend is installed.
 * It deliberately does not claim to perform neural face replacement.
 */
class PreviewFaceSwapEngine : FaceSwapEngine {
    override var avatarUri: String? = null
        private set

    override val isReady: Boolean
        get() = avatarUri != null

    override fun setAvatar(uri: String) {
        avatarUri = uri
    }

    override fun clearAvatar() {
        avatarUri = null
    }

    override fun close() = Unit
}
