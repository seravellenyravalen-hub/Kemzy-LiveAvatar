package com.kemzy.liveavatar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the camera session after the Activity leaves the foreground. */
class AvatarStreamingService : LifecycleService() {
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modelFrameBusy = AtomicBoolean(false)
    private lateinit var faceTracker: FaceTracker
    private lateinit var faceSwapEngine: FaceSwapEngine
    private var cameraProvider: ProcessCameraProvider? = null
    private var lockedReference: String? = null
    private var retryCount = 0
    private var modelRetryCount = 0
    private var virtualCameraBridge: VirtualCameraBridge? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        faceSwapEngine = OnDeviceFaceSwapEngine(applicationContext)
        virtualCameraBridge = StockAndroidVirtualCameraBridge(applicationContext)
        faceTracker = FaceTracker(
            onResult = { tracking, bitmap ->
                if (bitmap != null) {
                    if (!modelFrameBusy.compareAndSet(false, true)) {
                        bitmap.recycle()
                    } else {
                        modelExecutor.execute {
                            try {
                                if (faceSwapEngine.isReady && lockedReference != null) {
                                    val output = faceSwapEngine.processFrame(bitmap, tracking)
                                    // Never publish an unprocessed camera frame as the avatar.
                                    if (output.isNeural && output.bitmap != null) {
                                        StreamingFrameBus.publish(output.bitmap)
                                        virtualCameraBridge?.publishFrame(output.bitmap)
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                                modelFrameBusy.set(false)
                            }
                        }
                    }
                }
            },
            onError = { /* Internal diagnostics stay out of the normal UI. */ }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            return START_NOT_STICKY
        }

        val reference = intent?.getStringExtra(EXTRA_REFERENCE)
        if (reference.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (lockedReference == null) lockedReference = reference
        if (lockedReference != reference) return START_STICKY

        startCameraForeground()
        modelExecutor.execute { prepareAndStart(reference) }
        return START_STICKY
    }

    private fun startCameraForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun prepareAndStart(reference: String) {
        faceSwapEngine.setAvatar(reference)
        modelRetryCount = 0
        while (lockedReference == reference && !faceSwapEngine.isReady && modelRetryCount < MAX_MODEL_RETRIES) {
            try {
                faceSwapEngine.prepareAvatar()
            } catch (_: Throwable) {
                // Keep failures internal and retry with bounded backoff.
            }
            if (!faceSwapEngine.isReady) {
                modelRetryCount++
                if (modelRetryCount < MAX_MODEL_RETRIES) {
                    try {
                        Thread.sleep(modelBackoffMs(modelRetryCount))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                }
            }
        }
        if (lockedReference == reference && faceSwapEngine.isReady) bindCameraWithRetry()
    }

    private fun bindCameraWithRetry() {
        if (lockedReference == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase -> useCase.setAnalyzer(analysisExecutor) { image -> faceTracker.process(image) } }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                retryCount = 0
            } catch (_: Exception) {
                scheduleCameraRetry()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun scheduleCameraRetry() {
        if (lockedReference == null) return
        retryCount = (retryCount + 1).coerceAtMost(MAX_CAMERA_RETRIES)
        val delay = (500L * (1L shl (retryCount - 1).coerceAtMost(5))).coerceAtMost(15_000L)
        mainHandler.postDelayed({ bindCameraWithRetry() }, delay)
    }

    private fun stopStreaming() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        lockedReference = null
        retryCount = 0
        modelRetryCount = 0
        StreamingFrameBus.clear()
        faceSwapEngine.clearAvatar()
        mainHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kemzy_liveavatar)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.live_avatar_notification))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.live_avatar_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        faceTracker.close()
        faceSwapEngine.close()
        virtualCameraBridge?.close()
        virtualCameraBridge = null
        StreamingFrameBus.clear()
        mainHandler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun modelBackoffMs(attempt: Int): Long =
        (750L * (1L shl (attempt - 1).coerceAtMost(3))).coerceAtMost(6_000L)

    companion object {
        const val ACTION_STOP = "com.kemzy.liveavatar.action.STOP"
        const val EXTRA_REFERENCE = "com.kemzy.liveavatar.extra.REFERENCE"
        private const val CHANNEL_ID = "kemzy_live_avatar"
        private const val NOTIFICATION_ID = 9001
        private const val MAX_MODEL_RETRIES = 3
        private const val MAX_CAMERA_RETRIES = 8
    }
}
