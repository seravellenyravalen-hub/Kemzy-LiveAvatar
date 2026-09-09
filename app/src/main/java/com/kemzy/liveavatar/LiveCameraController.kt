package com.kemzy.liveavatar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val isReady: Boolean get() = faceSwapEngine.isReady
    val isRecording: Boolean get() = recording != null

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
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { image -> faceTracker.process(image) } }
                val recorder = Recorder.Builder().build()
                val video = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                    video
                )
                cameraProvider = provider
                videoCapture = video
            } catch (error: Exception) {
                onError("Camera could not start: ${error.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun pauseCamera() {
        recording?.close()
        recording = null
        cameraProvider?.unbindAll()
        videoCapture = null
    }

    fun startRecording() {
        val capture = videoCapture ?: run {
            onError("Camera recording is not ready")
            return
        }
        if (recording != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission required")
            return
        }

        val name = "Kemzy-LiveAvatar-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kemzy-LiveAvatar")
            }
        }
        val output = androidx.camera.video.MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        recording = capture.output
            .prepareRecording(context, output)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val uri = if (!event.hasError()) event.outputResults.outputUri else null
                    recording?.close()
                    recording = null
                    onRecordingFinalized(uri)
                    if (event.hasError()) onError("Recording failed: ${event.error}")
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun release() {
        liveEnabled = false
        recording?.close()
        recording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
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
                    if (output.isNeural && output.bitmap != null) onLiveFrame(output.bitmap)
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
