package com.kemzy.liveavatar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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

/**
 * Keeps the physical camera and real model pipeline alive while another app is
 * in the foreground. The foreground service does not claim to be a system
 * virtual camera; publishing to other apps requires an OS/OEM camera provider.
 */
class LiveCameraService : LifecycleService() {
    companion object {
        private const val CHANNEL_ID = "kemzy_live_camera"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.kemzy.liveavatar.START_LIVE"
        const val ACTION_STOP = "com.kemzy.liveavatar.STOP_LIVE"

        @Volatile
        var active: Boolean = false
            private set
    }

    private val pipeline = FramePipeline(capacity = 1)
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var sourceRepository: SourceFaceRepository
    private var runtime: OnDeviceSwapRuntime? = null

    override fun onCreate() {
        super.onCreate()
        sourceRepository = SourceFaceRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopLive()
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

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Kemzy-LiveAvatar")
            .setContentText("Live face processing is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        runtime = OnDeviceSwapRuntime(bundle)
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
                                onProcessed = { bitmap ->
                                    pipeline.offer(Frame(System.nanoTime(), bitmap))
                                },
                                onFrameError = { error ->
                                    android.util.Log.e("KemzyLive", "Model frame failed", error)
                                }
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
        runtime?.close()
        runtime = null
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        pipeline.clear()
        runtime?.close()
        runtime = null
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Kemzy live camera", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
