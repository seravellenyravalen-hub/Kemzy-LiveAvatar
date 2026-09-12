package com.kemzy.liveavatar.output

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

/** Latest-frame-only sink for the local Studio preview. */
class PreviewOutput(private val onFrame: (Bitmap) -> Unit) : LiveOutput {
    override val name: String = "Preview"
    private val latest = AtomicReference<Bitmap?>(null)

    override fun submit(frame: Bitmap) {
        val old = latest.getAndSet(frame)
        if (old != null && old !== frame && !old.isRecycled) old.recycle()
        onFrame(frame)
    }

    override fun stop() {
        latest.getAndSet(null)?.let { if (!it.isRecycled) it.recycle() }
    }
}
