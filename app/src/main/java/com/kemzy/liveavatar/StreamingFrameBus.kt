package com.kemzy.liveavatar

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

/** Small in-process handoff between the foreground camera service and the UI. */
object StreamingFrameBus {
    private val latest = AtomicReference<Bitmap?>(null)

    fun publish(frame: Bitmap) {
        val copy = frame.copy(Bitmap.Config.ARGB_8888, false)
        latest.getAndSet(copy)?.recycle()
    }

    fun take(): Bitmap? = latest.getAndSet(null)

    /** Returns a copy without consuming the frame, so independent sinks can use it. */
    fun snapshot(): Bitmap? = latest.get()?.let { current ->
        if (current.isRecycled) null else current.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun clear() {
        latest.getAndSet(null)?.recycle()
    }
}
