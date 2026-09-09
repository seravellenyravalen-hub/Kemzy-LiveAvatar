package com.kemzy.liveavatar

interface VoicePcmSource : AutoCloseable {
    val sampleRateHz: Int
    val channelCount: Int
    fun start(): Boolean
    fun read(target: ShortArray, offset: Int = 0, length: Int = target.size): Int
    fun stop()
    override fun close() = stop()
}
