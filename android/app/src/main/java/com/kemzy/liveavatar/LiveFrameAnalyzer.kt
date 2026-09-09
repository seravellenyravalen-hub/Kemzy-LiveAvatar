package com.kemzy.liveavatar

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX adapter: always keeps at most one pending frame to prevent lag buildup. */
class LiveFrameAnalyzer(
    private val pipeline: FramePipeline,
    private val onFrameAccepted: () -> Unit = {},
    private val onFrameDropped: () -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            image.close()
            onFrameDropped()
            return
        }
        try {
            pipeline.offer(Frame(image.imageInfo.timestamp))
            onFrameAccepted()
        } finally {
            image.close()
            processing.set(false)
        }
    }
}
