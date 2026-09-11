package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX adapter for the bounded live pipeline. */
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
            val raw = image.toBitmap()
            val bitmap = rotateForDisplay(raw, image.imageInfo.rotationDegrees)
            if (bitmap !== raw) raw.recycle()
            pipeline.offer(Frame(image.imageInfo.timestamp, bitmap))
            onFrameAccepted()
        } catch (error: Throwable) {
            onFrameError(error)
        } finally {
            image.close()
            processing.set(false)
        }
    }

    private fun rotateForDisplay(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
