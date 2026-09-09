package com.kemzy.liveavatar

/**
 * Boundary for a true system-wide virtual microphone. The app implementation
 * must fail closed when the system image does not provide one.
 */
interface VirtualMicrophoneBridge : AutoCloseable {
    val isAvailable: Boolean
    fun connect(sampleRateHz: Int, channelCount: Int): Boolean
    fun writePcm(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Int
    fun disconnect()
    override fun close() = disconnect()
}

class UnsupportedVirtualMicrophoneBridge : VirtualMicrophoneBridge {
    override val isAvailable: Boolean = false
    override fun connect(sampleRateHz: Int, channelCount: Int): Boolean = false
    override fun writePcm(samples: ShortArray, offset: Int, length: Int): Int = 0
    override fun disconnect() = Unit
}
