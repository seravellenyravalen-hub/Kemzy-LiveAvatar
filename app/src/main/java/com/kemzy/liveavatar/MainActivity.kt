package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val streamingReferenceLock = StreamingReferenceLock()
    private lateinit var referenceImageStore: ReferenceImageStore
    private lateinit var liveSessionStore: LiveSessionStore
    private lateinit var faceSwapEngine: FaceSwapEngine
    private lateinit var voiceAssetStore: VoiceAssetStore
    private lateinit var voiceController: LocalVoiceController
    private lateinit var previewView: PreviewView
    private lateinit var trackingAvatarView: ImageView
    private lateinit var avatarPreview: ImageView
    private lateinit var statusView: TextView
    private lateinit var voiceStatusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var selectAvatarButton: Button
    private lateinit var recordVoiceButton: Button
    private lateinit var playVoiceButton: Button

    private var currentVoiceFile: File? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var faceTracker: FaceTracker
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private val modelFrameBusy = AtomicBoolean(false)
    private val uiHandler = Handler(Looper.getMainLooper())

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || liveSessionStore.isActive) return@registerForActivityResult
        runCatching {
            val previous = currentAvatarUri()
            val localUri = referenceImageStore.persist(uri)
            if (!streamingReferenceLock.select(localUri)) {
                referenceImageStore.delete(localUri)
                return@runCatching
            }
            previous?.let(referenceImageStore::delete)
            saveAvatarUri(localUri)
            faceSwapEngine.setAvatar(localUri)
            avatarPreview.setImageURI(Uri.parse(localUri))
            avatarPreview.visibility = View.VISIBLE
            prepareAvatarEngine()
            updateControls()
        }.onFailure {
            statusView.text = "Could not import avatar image"
        }
    }

    private val pickVoice = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            currentVoiceFile = voiceAssetStore.import(uri)
            voiceStatusView.text = "Voice imported locally"
            playVoiceButton.isEnabled = true
        }.onFailure {
            voiceStatusView.text = "Voice import unavailable"
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bindCameraForForeground() else statusView.text = "Camera permission required"
        updateControls()
    }

    private val requestMicrophonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecording() else voiceStatusView.text = "Microphone permission required"
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (!(application as PrivacyApplication).unlocked) {
            startActivity(Intent(this, PrivacyLockActivity::class.java))
            finish()
            return
        }

        referenceImageStore = ReferenceImageStore(applicationContext)
        liveSessionStore = LiveSessionStore(applicationContext)
        voiceAssetStore = VoiceAssetStore(applicationContext)
        voiceController = LocalVoiceController(applicationContext)
        faceSwapEngine = OnDeviceFaceSwapEngine(applicationContext)

        faceTracker = FaceTracker(
            onResult = onFrame@{ tracking, bitmap ->
                if (bitmap == null) return@onFrame
                if (!liveSessionStore.isActive || !faceSwapEngine.isReady) {
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
            onError = { error ->
                runOnUiThread {
                    if (liveSessionStore.isActive) {
                        statusView.text = "Camera/face tracking error: ${error.message ?: "unknown error"}"
                    }
                }
            }
        )

        buildUi()
        restoreSessionAndAvatar()

        if (hasCameraPermission()) {
            bindCameraForForeground()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        uiHandler.post(framePoller)
        if (liveSessionStore.isActive) {
            sendServiceAction(AvatarStreamingService.ACTION_FOREGROUND)
            uiHandler.postDelayed({ bindCameraForForeground() }, 200L)
        } else if (currentAvatarUri() != null && !faceSwapEngine.isReady) {
            prepareAvatarEngine()
        }
    }

    override fun onStop() {
        uiHandler.removeCallbacks(framePoller)
        unbindForegroundCamera()
        if (liveSessionStore.isActive) {
            sendServiceAction(AvatarStreamingService.ACTION_BACKGROUND)
        }
        super.onStop()
    }

    private val framePoller = object : Runnable {
        override fun run() {
            if (liveSessionStore.isActive) {
                StreamingFrameBus.take()?.let { frame ->
                    trackingAvatarView.setImageBitmap(frame)
                    trackingAvatarView.visibility = View.VISIBLE
                }
            }
            uiHandler.postDelayed(this, 33L)
        }
    }

    private fun restoreSessionAndAvatar() {
        val reference = liveSessionStore.reference ?: currentAvatarUri()
        if (!reference.isNullOrBlank()) {
            saveAvatarUri(reference)
            streamingReferenceLock.select(reference)
            avatarPreview.setImageURI(Uri.parse(reference))
            avatarPreview.visibility = View.VISIBLE
            faceSwapEngine.setAvatar(reference)
            if (!liveSessionStore.isActive) prepareAvatarEngine()
        }
        if (liveSessionStore.isActive) {
            streamingReferenceLock.beginStreaming()
            previewView.visibility = View.INVISIBLE
            trackingAvatarView.visibility = View.VISIBLE
        }
    }

    private fun prepareAvatarEngine() {
        val reference = currentAvatarUri() ?: return
        faceSwapEngine.setAvatar(reference)
        statusView.text = "Preparing local AI models…"
        modelExecutor.execute {
            faceSwapEngine.prepareAvatar()
            runOnUiThread { updateControls() }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Kemzy-LiveAvatar"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(16, 18, 16, 10)
        })

        val container = FrameLayout(this)
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        container.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        trackingAvatarView = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        container.addView(trackingAvatarView, FrameLayout.LayoutParams(-1, -1))
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))

        val avatarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 4)
        }
        avatarPreview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected avatar"
            setBackgroundColor(0xFF181818.toInt())
        }
        avatarRow.addView(avatarPreview, LinearLayout.LayoutParams(72, 72))
        avatarRow.addView(TextView(this).apply {
            text = "Reference image"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setPadding(12, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(avatarRow)

        selectAvatarButton = Button(this).apply {
            text = "Choose Avatar"
            setOnClickListener { pickAvatar.launch("image/*") }
        }
        root.addView(selectAvatarButton, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(16, 2, 16, 2)
        })

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        utilityRow.addView(Button(this).apply {
            text = "Device Setup"
            setOnClickListener { startActivity(Intent(this@MainActivity, DeviceSetupActivity::class.java)) }
        })
        root.addView(utilityRow)

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            text = "Camera starting…"
            setPadding(16, 8, 16, 8)
        }
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 2, 16, 6)
        }
        startButton = Button(this).apply { text = "Start"; setOnClickListener { startSession() } }
        stopButton = Button(this).apply { text = "Stop"; setOnClickListener { stopSession() } }
        controls.addView(startButton)
        controls.addView(stopButton)
        root.addView(controls)

        voiceStatusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            text = "Voice: local recording/import"
            setPadding(16, 2, 16, 2)
        }
        root.addView(voiceStatusView)

        val voiceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 2, 8, 8)
        }
        recordVoiceButton = Button(this).apply { text = "Record Voice"; setOnClickListener { toggleRecording() } }
        val importVoiceButton = Button(this).apply {
            text = "Import Voice"
            setOnClickListener { pickVoice.launch(arrayOf("audio/*")) }
        }
        playVoiceButton = Button(this).apply {
            text = "Play"
            isEnabled = false
            setOnClickListener { currentVoiceFile?.let { voiceController.play(it) } }
        }
        voiceControls.addView(recordVoiceButton)
        voiceControls.addView(importVoiceButton)
        voiceControls.addView(playVoiceButton)
        root.addView(voiceControls)

        setContentView(root)
    }

    private fun startSession() {
        if (!hasCameraPermission()) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val reference = currentAvatarUri()
        if (reference.isNullOrBlank()) {
            statusView.text = "Choose an avatar first"
            return
        }
        if (!faceSwapEngine.isReady) {
            statusView.text = "AI models are not ready yet"
            prepareAvatarEngine()
            return
        }
        if (!streamingReferenceLock.select(reference) || !streamingReferenceLock.beginStreaming()) return

        liveSessionStore.markActive(reference)
        StreamingFrameBus.clear()
        val intent = Intent(this, AvatarStreamingService::class.java)
            .setAction(AvatarStreamingService.ACTION_FOREGROUND)
            .putExtra(AvatarStreamingService.EXTRA_REFERENCE, reference)
        ContextCompat.startForegroundService(this, intent)
        previewView.visibility = View.VISIBLE
        trackingAvatarView.visibility = View.VISIBLE
        statusView.text = "Live avatar active"
        bindCameraForForeground()
        updateControls()
    }

    private fun stopSession() {
        unbindForegroundCamera()
        startService(Intent(this, AvatarStreamingService::class.java).setAction(AvatarStreamingService.ACTION_STOP))
        liveSessionStore.clear()
        streamingReferenceLock.stopStreaming()
        currentAvatarUri()?.let(referenceImageStore::delete)
        clearAvatarUri()
        StreamingFrameBus.clear()
        trackingAvatarView.setImageDrawable(null)
        trackingAvatarView.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        updateControls()
    }

    private fun toggleRecording() {
        if (voiceController.isRecording) {
            val file = voiceController.stopRecording()
            currentVoiceFile = file
            recordVoiceButton.text = "Record Voice"
            voiceStatusView.text = if (file != null) "Voice saved locally" else "Recording failed"
            playVoiceButton.isEnabled = file?.isFile == true
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else {
            requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecording() {
        runCatching {
            voiceController.startRecordingAndRemember(voiceAssetStore.newRecordingFile())
            recordVoiceButton.text = "Stop Recording"
            voiceStatusView.text = "Recording locally"
        }.onFailure {
            voiceStatusView.text = "Recording failed: ${it.message ?: "unknown error"}"
        }
    }

    private fun bindCameraForForeground() {
        if (!hasCameraPermission()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed) return@addListener
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { image -> faceTracker.process(image) }
                    }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                previewView.visibility = View.VISIBLE
                if (!liveSessionStore.isActive) statusView.text = "Camera ready"
            }.onFailure {
                statusView.text = "Camera initialization failed: ${it.message ?: "unknown error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindForegroundCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun sendServiceAction(action: String) {
        val reference = liveSessionStore.reference ?: currentAvatarUri() ?: return
        startService(
            Intent(this, AvatarStreamingService::class.java)
                .setAction(action)
                .putExtra(AvatarStreamingService.EXTRA_REFERENCE, reference)
        )
    }

    private fun updateControls() {
        val running = liveSessionStore.isActive
        val hasAvatar = currentAvatarUri() != null
        selectAvatarButton.isEnabled = !running
        startButton.isEnabled = hasCameraPermission() && hasAvatar && faceSwapEngine.isReady && !running
        stopButton.isEnabled = running
        if (!running) {
            statusView.text = when {
                !hasCameraPermission() -> "Camera permission required"
                !hasAvatar -> "Choose an avatar to begin"
                faceSwapEngine.state is LiveFaceEngineState.Fallback ->
                    (faceSwapEngine.state as LiveFaceEngineState.Fallback).reason
                faceSwapEngine.state is LiveFaceEngineState.Ready -> "AI face engine ready — press Start"
                else -> "Preparing local AI models…"
            }
        }
    }

    private fun currentAvatarUri(): String? = getPreferences(MODE_PRIVATE).getString("avatar_uri", null)

    private fun saveAvatarUri(uri: String) {
        getPreferences(MODE_PRIVATE).edit().putString("avatar_uri", uri).apply()
    }

    private fun clearAvatarUri() {
        getPreferences(MODE_PRIVATE).edit().remove("avatar_uri").apply()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            (application as PrivacyApplication).lock()
        }
        unbindForegroundCamera()
        faceTracker.close()
        faceSwapEngine.close()
        voiceController.close()
        analysisExecutor.shutdownNow()
        modelExecutor.shutdownNow()
        super.onDestroy()
    }
}
