package com.kemzy.liveavatar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmRingBufferTest {
    @Test
    fun dropsOldestSamplesWhenCapacityIsExceeded() {
        val buffer = PcmRingBuffer(4)
        buffer.write(shortArrayOf(1, 2, 3, 4))
        buffer.write(shortArrayOf(5, 6))
        val output = ShortArray(4)
        assertEquals(4, buffer.read(output))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), output)
        assertEquals(1L, buffer.overruns())
    }

    @Test
    fun underrunIsCountedWithoutBlocking() {
        val buffer = PcmRingBuffer(4)
        val output = ShortArray(2)
        assertEquals(0, buffer.read(output))
        assertEquals(1L, buffer.underruns())
    }
}
