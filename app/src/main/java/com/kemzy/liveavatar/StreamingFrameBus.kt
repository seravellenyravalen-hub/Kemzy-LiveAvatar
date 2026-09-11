package com.kemzy.liveavatar

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Small in-process handoff between the foreground camera service and live camera sinks. */
object StreamingFrameBus {
    private val latest = AtomicReference<Bitmap?>(null)
    private val lastPublishedAt = AtomicLong(0L)

    fun publish(frame: Bitmap) {
        val copy = frame.copy(Bitmap.Config.ARGB_8888, false)
        latest.getAndSet(copy)?.recycle()
        lastPublishedAt.set(System.currentTimeMillis())
    }

    fun take(): Bitmap? = latest.getAndSet(null)

    /** Returns a copy without consuming the frame, so independent sinks can use it. */
    fun snapshot(maxAgeMillis: Long = Long.MAX_VALUE): Bitmap? {
        val current = latest.get() ?: return null
        if (current.isRecycled) return null
        val age = System.currentTimeMillis() - lastPublishedAt.get()
        if (age < 0L || age > maxAgeMillis) return null
        return current.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun ageMillis(): Long {
        val published = lastPublishedAt.get()
        return if (published == 0L) Long.MAX_VALUE
        else (System.currentTimeMillis() - published).coerceAtLeast(0L)
    }

    fun clear() {
        latest.getAndSet(null)?.recycle()
        lastPublishedAt.set(0L)
    }
}
