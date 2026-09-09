package com.kemzy.liveavatar

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX adapter for the bounded live pipeline.
 *
 * The bitmap conversion happens while the ImageProxy is still open. CameraX provides
 * ImageProxy.toBitmap() for YUV_420_888/JPEG/RGBA_8888 analysis frames. The converted
 * bitmap is then owned by the FramePipeline and the proxy is always closed promptly.
 */
class LiveFrameAnalyzer(
    private val pipeline: FramePipeline,
    private val onFrameAccepted: () -> Unit = {},
    private val onFrameDropped: () -> Unit = {},
    private val onFrameError: (Throwable) -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            image.close()
            onFrameDropped()
            return
        }

        try {
            val bitmap = image.toBitmap()
            pipeline.offer(Frame(image.imageInfo.timestamp, bitmap))
            onFrameAccepted()
        } catch (error: Throwable) {
            onFrameError(error)
        } finally {
            image.close()
            processing.set(false)
        }
    }
}
