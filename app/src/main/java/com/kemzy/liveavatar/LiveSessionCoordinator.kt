package com.kemzy.liveavatar

/** Single in-process source of truth for the immutable Live session. */
class LiveSessionCoordinator {
    private val camera = CameraSessionPolicy()
    private val voice = VoiceSessionPolicy()

    val isActive: Boolean get() = camera.isRunning
    val reference: String? get() = if (camera.isRunning) referenceValue else null
    val voiceSource: VoiceSourceType? get() = voice.selected

    private var referenceValue: String? = null

    @Synchronized
    fun start(referenceUri: String): Boolean {
        if (referenceUri.isBlank() || camera.isRunning) return false
        camera.start(referenceUri)
        referenceValue = referenceUri
        return true
    }

    @Synchronized
    fun lockVoice(source: VoiceSourceType): Boolean = voice.select(source)

    @Synchronized
    fun beginVoice(): Boolean = voice.beginStreaming()

    @Synchronized
    fun onActivityHidden() = camera.onActivityHidden()

    @Synchronized
    fun onNetworkChanged() = camera.onNetworkChanged()

    @Synchronized
    fun stop() {
        camera.stop()
        voice.stopStreaming()
        referenceValue = null
    }

    fun isReferenceLockedTo(uri: String): Boolean = camera.isReferenceLockedTo(uri)
}
