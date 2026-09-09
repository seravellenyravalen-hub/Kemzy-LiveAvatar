package com.kemzy.liveavatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground CameraX owner for real preview + live neural processing. */
class LiveCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val engine: FaceSwapEngine,
    private val onProcessedFrame: (android.graphics.Bitmap) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private val modelBusy = AtomicBoolean(false)
    private lateinit var tracker: FaceTracker
    private var provider: ProcessCameraProvider? = null
    private var running = false

    init {
        tracker = FaceTracker(
            onResult = { tracking, bitmap ->
                if (running && bitmap != null && modelBusy.compareAndSet(false, true)) {
                    modelExecutor.execute {
                        try {
                            val output = engine.processFrame(bitmap, tracking)
                            if (output.isNeural && output.bitmap != null && running) {
                                onProcessedFrame(output.bitmap)
                            } else {
                                output.bitmap?.recycle()
                            }
                        } catch (error: Exception) {
                            onStatus("Live processing error: ${error.message ?: "unknown"}")
                        } finally {
                            bitmap.recycle()
                            modelBusy.set(false)
                        }
                    }
                } else {
                    bitmap?.recycle()
                }
            },
            onError = { onStatus("Face tracking error: ${it.message ?: "unknown"}") }
        )
    }

    fun start() {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Camera permission required")
            return
        }
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { useCase -> useCase.setAnalyzer(analysisExecutor) { image -> tracker.process(image) } }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                onStatus("Camera live — processing selected face")
            } catch (error: Exception) {
                running = false
                onStatus("Camera could not start: ${error.message ?: "unknown"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        provider?.unbindAll()
        provider = null
        StreamingFrameBus.clear()
    }

    fun close() {
        stop()
        tracker.close()
        analysisExecutor.shutdownNow()
        modelExecutor.shutdownNow()
    }
}
