package com.kemzy.liveavatar

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one voice source for a session and forwards bounded PCM to the system bridge. */
class VoiceSessionController(
    private val microphoneSource: VoicePcmSource,
    private val bridge: VirtualMicrophoneBridge = UnsupportedVirtualMicrophoneBridge(),
    private val buffer: PcmRingBuffer = PcmRingBuffer(48_000)
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val policy = VoiceSessionPolicy()
    private val scratch = ShortArray(960)

    fun startMicrophone(): Boolean {
        if (!policy.select(VoiceSourceType.MICROPHONE) || !policy.beginStreaming()) return false
        if (!microphoneSource.start()) {
            policy.stopStreaming()
            return false
        }
        if (bridge.isAvailable && !bridge.connect(microphoneSource.sampleRateHz, microphoneSource.channelCount)) {
            microphoneSource.stop()
            policy.stopStreaming()
            return false
        }
        running.set(true)
        executor.execute {
            while (running.get()) {
                val count = microphoneSource.read(scratch)
                if (count > 0) {
                    buffer.write(scratch, 0, count)
                    if (bridge.isAvailable) bridge.writePcm(scratch, 0, count)
                }
            }
        }
        return true
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            policy.stopStreaming()
            microphoneSource.stop()
            bridge.disconnect()
            buffer.clear()
            return
        }
        microphoneSource.stop()
        bridge.disconnect()
        buffer.clear()
        policy.stopStreaming()
    }

    fun isUsing(source: VoiceSourceType): Boolean = policy.isLockedTo(source)
    fun bufferedSamples(): Int = buffer.availableSamples()

    override fun close() {
        stop()
        executor.shutdownNow()
        bridge.close()
    }
}
