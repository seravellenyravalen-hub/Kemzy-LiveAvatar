package com.kemzy.liveavatar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveCameraService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID = "kemzy_live_camera"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.kemzy.liveavatar.START_LIVE"
        const val ACTION_STOP = "com.kemzy.liveavatar.STOP_LIVE"
        const val ACTION_SET_VOICE_MODE = "com.kemzy.liveavatar.SET_VOICE_MODE"
        const val EXTRA_VOICE_MODE = "voice_mode"

        @Volatile var active: Boolean = false
            private set
        @Volatile var selectedVoiceMode: VoiceEffectProcessor.Mode = VoiceEffectProcessor.Mode.NATURAL
    }

    private val pipeline = FramePipeline(capacity = 1)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var sourceRepository: SourceFaceRepository
    private var runtime: OnDeviceSwapRuntime? = null
    private var voiceCapture: VoiceCaptureController? = null

    override fun onCreate() {
        super.onCreate()
        sourceRepository = SourceFaceRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopLive()
            ACTION_SET_VOICE_MODE -> {
                intent.getStringExtra(EXTRA_VOICE_MODE)?.let { value ->
                    runCatching { selectedVoiceMode = VoiceEffectProcessor.Mode.valueOf(value) }
                    voiceCapture?.setMode(selectedVoiceMode)
                }
            }
            else -> startLive()
        }
        return START_NOT_STICKY
    }

    private fun startLive() {
        if (active) return
        val bundle = ModelBundleRepository(ModelRepository(this)).inspect()
        if (!bundle.isComplete || !sourceRepository.hasSource()) {
            android.util.Log.e("KemzyLive", "Background Live Swap requires source image and complete model bundle")
            stopSelf()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("KemzyLive", "Camera and microphone permissions are required")
            stopSelf()
            return
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Kemzy-LiveAvatar")
            .setContentText("Live face and voice processing is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        runtime = OnDeviceSwapRuntime(bundle)
        voiceCapture = VoiceCaptureController(this).also { capture ->
            capture.setMode(selectedVoiceMode)
            capture.start { processed -> VoiceOutputBuffer.offer(processed) }
        }
        active = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            if (!active) return@addListener
            runCatching {
                val provider = cameraProviderFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(
                            cameraExecutor,
                            LiveSwapAnalyzer(
                                runtime = requireNotNull(runtime),
                                sourceProvider = { sourceRepository.loadSource() },
                                onProcessed = { bitmap -> pipeline.offer(Frame(System.nanoTime(), bitmap)) },
                                onFrameError = { error -> android.util.Log.e("KemzyLive", "Model frame failed", error) }
                            )
                        )
                    }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            }.onFailure {
                android.util.Log.e("KemzyLive", "Unable to start background camera", it)
                stopLive()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopLive() {
        active = false
        pipeline.clear()
        VoiceOutputBuffer.clear()
        voiceCapture?.close()
        voiceCapture = null
        runtime?.close()
        runtime = null
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        pipeline.clear()
        VoiceOutputBuffer.clear()
        voiceCapture?.close()
        voiceCapture = null
        runtime?.close()
        runtime = null
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kemzy live camera", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

object VoiceOutputBuffer {
    private const val MAX_FRAMES = 8
    private val queue = java.util.concurrent.ArrayBlockingQueue<ShortArray>(MAX_FRAMES)
    fun offer(frame: ShortArray) {
        if (!queue.offer(frame)) { queue.poll(); queue.offer(frame) }
    }
    fun poll(): ShortArray? = queue.poll()
    fun clear() = queue.clear()
}
