package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingReferenceLockTest {
    @Test
    fun referenceCanChangeBeforeStreamingStarts() {
        val lock = StreamingReferenceLock()

        assertTrue(lock.select("avatar-a"))
        assertTrue(lock.select("avatar-b"))
        assertEquals("avatar-b", lock.selected)
    }

    @Test
    fun referenceCannotChangeOnceStreamingStarts() {
        val lock = StreamingReferenceLock()
        lock.select("avatar-a")

        assertTrue(lock.beginStreaming())
        assertFalse(lock.select("avatar-b"))
        assertEquals("avatar-a", lock.selected)
    }

    @Test
    fun stoppingStreamingReleasesTheReferenceLock() {
        val lock = StreamingReferenceLock()
        lock.select("avatar-a")
        lock.beginStreaming()

        lock.stopStreaming()

        assertTrue(lock.select("avatar-b"))
        assertEquals("avatar-b", lock.selected)
    }

    @Test
    fun networkChangesDoNotAffectTheLockedReference() {
        val lock = StreamingReferenceLock()
        lock.select("avatar-a")
        lock.beginStreaming()

        lock.onNetworkChanged()
        lock.onNetworkChanged()

        assertEquals("avatar-a", lock.selected)
        assertTrue(lock.isStreaming)
    }
}
