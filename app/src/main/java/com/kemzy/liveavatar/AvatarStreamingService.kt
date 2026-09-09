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

/** Keeps the camera/AI pipeline alive only while the app is backgrounded. */
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        faceSwapEngine = OnDeviceFaceSwapEngine(applicationContext)
        faceTracker = FaceTracker(
            onResult = onFrame@{ tracking, bitmap ->
                if (bitmap == null) return@onFrame
                if (!faceSwapEngine.isReady) {
                    bitmap.recycle()
                    return@onFrame
                }
                if (!modelFrameBusy.compareAndSet(false, true)) {
                    bitmap.recycle()
                    return@onFrame
                }
                modelExecutor.execute {
                    try {
                        val output = faceSwapEngine.processFrame(bitmap, tracking)
                        if (output.isNeural && output.bitmap != null) {
                            StreamingFrameBus.publish(output.bitmap)
                        }
                    } finally {
                        bitmap.recycle()
                        modelFrameBusy.set(false)
                    }
                }
            },
            onError = { /* MainActivity presents foreground diagnostics. */ }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopStreaming()
            return START_NOT_STICKY
        }

        val reference = intent?.getStringExtra(EXTRA_REFERENCE) ?: lockedReference
        if (reference.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (lockedReference == null) lockedReference = reference
        if (lockedReference != reference) return START_STICKY

        startForegroundWithCameraType()

        if (action == ACTION_FOREGROUND) {
            // Activity owns Preview + ImageAnalysis while visible. Do not initialize a
            // second camera pipeline here; just release any background camera use case.
            unbindCamera()
            return START_STICKY
        }

        modelExecutor.execute {
            faceSwapEngine.setAvatar(reference)
            faceSwapEngine.prepareAvatar()
            if (faceSwapEngine.isReady) bindCameraWithRetry()
        }
        return START_STICKY
    }

    private fun startForegroundWithCameraType() {
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

    private fun bindCameraWithRetry() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { image -> faceTracker.process(image) }
                    }
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
        retryCount = (retryCount + 1).coerceAtMost(8)
        val delay = (500L * (1L shl (retryCount - 1).coerceAtMost(5))).coerceAtMost(15_000L)
        mainHandler.postDelayed({ bindCameraWithRetry() }, delay)
    }

    private fun unbindCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        retryCount = 0
    }

    private fun stopStreaming() {
        unbindCamera()
        lockedReference = null
        StreamingFrameBus.clear()
        faceSwapEngine.clearAvatar()
        mainHandler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kemzy_liveavatar)
            .setContentTitle("Kemzy-LiveAvatar")
            .setContentText("Live avatar camera is active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live Avatar", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as PrivacyApplication).lock()
        stopStreaming()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unbindCamera()
        faceTracker.close()
        faceSwapEngine.close()
        StreamingFrameBus.clear()
        mainHandler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kemzy.liveavatar.action.STOP"
        const val ACTION_FOREGROUND = "com.kemzy.liveavatar.action.FOREGROUND"
        const val ACTION_BACKGROUND = "com.kemzy.liveavatar.action.BACKGROUND"
        const val EXTRA_REFERENCE = "com.kemzy.liveavatar.extra.REFERENCE"
        private const val CHANNEL_ID = "kemzy_live_avatar"
        private const val NOTIFICATION_ID = 9001
    }
}
