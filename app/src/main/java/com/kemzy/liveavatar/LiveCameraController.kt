package com.kemzy.liveavatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiveCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onLiveFrame: (android.graphics.Bitmap) -> Unit,
    private val onRecordingFinalized: (Uri?) -> Unit,
    private val onError: (String) -> Unit
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private val modelBusy = AtomicBoolean(false)
    private var liveEnabled = false
    private val faceTracker = FaceTracker(
        onResult = { tracking, bitmap -> processTrackedFrame(tracking, bitmap) },
        onError = { onError(it.message ?: "Face tracking failed") }
    )
    private val faceSwapEngine = OnDeviceFaceSwapEngine(context.applicationContext)
    private val avatarRecorder = AvatarSurfaceRecorder(context.applicationContext)
    private var cameraProvider: ProcessCameraProvider? = null

    val isReady: Boolean get() = faceSwapEngine.isReady
    val isRecording: Boolean get() = avatarRecorder.isRecording

    fun setAvatar(uri: String) {
        if (faceSwapEngine.avatarUri != uri) {
            faceSwapEngine.clearAvatar()
            faceSwapEngine.setAvatar(uri)
        }
    }

    fun setLiveEnabled(enabled: Boolean) {
        liveEnabled = enabled
    }

    fun prepareAvatarAsync(onComplete: (LiveFaceEngineState) -> Unit) {
        modelExecutor.execute {
            val state = faceSwapEngine.prepareAvatar()
            ContextCompat.getMainExecutor(context).execute { onComplete(state) }
        }
    }

    fun bindCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError("Camera permission required")
            return
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // Keep the preview independent from analysis resolution. The neural live
                // path only needs a modest frame size, while full camera-resolution
                // ImageProxy.toBitmap() can allocate a large ARGB buffer and exceed the
                // ~128 MB heap on lower-memory Android devices.
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { image -> faceTracker.process(image) } }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                cameraProvider = provider
            } catch (error: Exception) {
                onError("Camera could not start: ${error.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun pauseCamera() {
        cameraProvider?.unbindAll()
        if (avatarRecorder.isRecording) {
            val uri = avatarRecorder.stop()
            onRecordingFinalized(uri)
        }
    }

    fun startRecording() {
        if (!liveEnabled) {
            onError("Start Live before recording")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission required")
            return
        }
        if (avatarRecorder.isRecording) return
        if (!avatarRecorder.start()) {
            onError("Avatar recording could not start on this device")
        }
    }

    fun stopRecording() {
        if (!avatarRecorder.isRecording) return
        val uri = avatarRecorder.stop()
        onRecordingFinalized(uri)
        if (uri == null) onError("Recording could not be saved")
    }

    fun release() {
        liveEnabled = false
        if (avatarRecorder.isRecording) {
            val uri = avatarRecorder.stop()
            onRecordingFinalized(uri)
        } else {
            avatarRecorder.release()
        }
        cameraProvider?.unbindAll()
        cameraProvider = null
        faceTracker.close()
        faceSwapEngine.close()
        cameraExecutor.shutdownNow()
        modelExecutor.shutdownNow()
    }

    private fun processTrackedFrame(tracking: FaceTrackingResult, bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) return
        if (!liveEnabled) {
            bitmap.recycle()
            return
        }
        if (!modelBusy.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }
        modelExecutor.execute {
            try {
                if (faceSwapEngine.isReady && liveEnabled) {
                    val output = faceSwapEngine.processFrame(bitmap, tracking)
                    if (output.isNeural && output.bitmap != null) {
                        val generated = output.bitmap
                        if (avatarRecorder.isRecording) avatarRecorder.drawFrame(generated)
                        ContextCompat.getMainExecutor(context).execute { onLiveFrame(generated) }
                    }
                }
            } catch (error: Exception) {
                onError(error.message ?: "Live avatar processing failed")
            } finally {
                bitmap.recycle()
                modelBusy.set(false)
            }
        }
    }
}
