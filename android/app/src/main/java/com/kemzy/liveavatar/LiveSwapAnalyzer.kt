package com.kemzy.liveavatar

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time CameraX analyzer that runs the installed Deep-Live-Cam model chain.
 * It never fabricates frames: if the model bundle is unavailable, it reports an
 * error and leaves the camera frame unchanged for the caller.
 */
class LiveSwapAnalyzer(
    private val runtime: OnDeviceSwapRuntime,
    private val sourceProvider: () -> android.graphics.Bitmap?,
    private val onProcessed: (android.graphics.Bitmap) -> Unit,
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
            val source = sourceProvider()
                ?: error("Select a source face before starting Live Swap")
            val target = image.toBitmap()
            val output = runtime.process(source, target)
            onProcessed(output)
        } catch (error: Throwable) {
            onFrameError(error)
        } finally {
            image.close()
            processing.set(false)
        }
    }
}
