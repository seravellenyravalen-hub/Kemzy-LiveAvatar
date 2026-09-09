package com.kemzy.liveavatar

import java.util.concurrent.atomic.AtomicLong

/**
 * Fixed-capacity PCM16 mono ring buffer. It deliberately drops the oldest audio
 * when full so a stalled consumer cannot create unbounded latency or memory use.
 */
class PcmRingBuffer(capacitySamples: Int) {
    init { require(capacitySamples > 0) }

    private val buffer = ShortArray(capacitySamples)
    private var head = 0
    private var size = 0
    private val overrunCount = AtomicLong(0)
    private val underrunCount = AtomicLong(0)

    @Synchronized
    fun write(input: ShortArray, offset: Int = 0, length: Int = input.size): Int {
        require(offset >= 0 && length >= 0 && offset + length <= input.size)
        if (length == 0) return 0
        var start = offset
        var count = length
        if (count >= buffer.size) {
            start = offset + count - buffer.size
            count = buffer.size
            overrunCount.incrementAndGet()
            head = 0
            size = 0
        } else if (size + count > buffer.size) {
            val drop = size + count - buffer.size
            head = (head + drop) % buffer.size
            size -= drop
            overrunCount.incrementAndGet()
        }
        var index = (head + size) % buffer.size
        repeat(count) {
            buffer[index] = input[start + it]
            index = (index + 1) % buffer.size
        }
        size += count
        return count
    }

    @Synchronized
    fun read(output: ShortArray, offset: Int = 0, length: Int = output.size): Int {
        require(offset >= 0 && length >= 0 && offset + length <= output.size)
        if (length == 0) return 0
        val count = minOf(length, size)
        var index = head
        repeat(count) {
            output[offset + it] = buffer[index]
            index = (index + 1) % buffer.size
        }
        head = (head + count) % buffer.size
        size -= count
        if (count < length) underrunCount.incrementAndGet()
        return count
    }

    @Synchronized fun availableSamples(): Int = size
    @Synchronized fun clear() { head = 0; size = 0 }
    fun overruns(): Long = overrunCount.get()
    fun underruns(): Long = underrunCount.get()
}
