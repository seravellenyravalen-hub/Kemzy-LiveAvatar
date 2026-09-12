package com.kemzy.liveavatar.voice

import java.io.Closeable

interface VoiceEngine : Closeable {
    val mode: VoiceMode
    fun start(): Result<Unit>
    fun stop()
    fun setLipSyncListener(listener: (Float) -> Unit)
}

enum class VoiceMode { NORMAL, MALE, FEMALE, IMPORTED }

/**
 * Safe baseline voice path. It exposes microphone/lip-sync timing without
 * pretending that a neural voice converter exists on the target device.
 */
class PassthroughVoiceEngine : VoiceEngine {
    override val mode: VoiceMode = VoiceMode.NORMAL
    private var listener: ((Float) -> Unit)? = null
    private var running = false

    override fun start(): Result<Unit> { running = true; return Result.success(Unit) }
    override fun stop() { running = false }
    override fun setLipSyncListener(listener: (Float) -> Unit) { this.listener = listener }
    fun publishMouthActivity(value: Float) { if (running) listener?.invoke(value.coerceIn(0f, 1f)) }
    override fun close() = stop()
}

class UnsupportedVoiceConverter(private val requestedMode: VoiceMode) : VoiceEngine {
    override val mode: VoiceMode = requestedMode
    override fun start(): Result<Unit> = Result.failure(
        IllegalStateException("$requestedMode neural voice conversion is not installed and cannot be enabled yet")
    )
    override fun stop() = Unit
    override fun setLipSyncListener(listener: (Float) -> Unit) = Unit
    override fun close() = Unit
}
