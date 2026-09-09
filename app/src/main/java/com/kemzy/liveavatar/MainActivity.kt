package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private val streamingReferenceLock = StreamingReferenceLock()
    private lateinit var referenceImageStore: ReferenceImageStore
    private lateinit var liveSessionStore: LiveSessionStore
    private lateinit var faceSwapEngine: FaceSwapEngine
    private lateinit var voiceAssetStore: VoiceAssetStore
    private lateinit var voiceController: LocalVoiceController
    private lateinit var liveCameraController: LiveCameraController
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var processedView: ImageView
    private lateinit var avatarPreview: ImageView
    private lateinit var statusView: TextView
    private lateinit var voiceStatusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var selectAvatarButton: Button
    private lateinit var recordVoiceButton: Button
    private lateinit var playVoiceButton: Button
    private var currentVoiceFile: File? = null
    private var currentProcessedBitmap: android.graphics.Bitmap? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || liveSessionStore.isActive) return@registerForActivityResult
        runCatching {
            val previous = currentAvatarUri()
            val localUri = referenceImageStore.persist(uri)
            if (!streamingReferenceLock.select(localUri)) {
                referenceImageStore.delete(localUri)
                return@runCatching
            }
            previous?.let { if (it != localUri) referenceImageStore.delete(it) }
            saveAvatarUri(localUri)
            faceSwapEngine.setAvatar(localUri)
            avatarPreview.setImageURI(Uri.parse(localUri))
            avatarPreview.visibility = View.VISIBLE
            statusView.text = "Connecting to Deep-Live-Cam…"
            prepareAvatarEngine()
            updateControls()
        }.onFailure { statusView.text = "Avatar import failed" }
    }

    private val pickVoice = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            currentVoiceFile = voiceAssetStore.import(uri)
            voiceStatusView.text = "Voice imported locally"
            playVoiceButton.isEnabled = true
        }.onFailure { voiceStatusView.text = "Voice import unavailable" }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        updateControls()
        if (!granted) statusView.text = "Camera permission required"
    }

    private val requestMicrophonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecording() else voiceStatusView.text = "Microphone permission required"
    }

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
        faceSwapEngine = RemoteDeepLiveFaceSwapEngine(applicationContext)
        buildUi()
        liveCameraController = LiveCameraController(
            context = applicationContext,
            lifecycleOwner = this,
            previewView = previewView,
            engine = faceSwapEngine,
            onProcessedFrame = { bitmap ->
                runOnUiThread {
                    currentProcessedBitmap?.recycle()
                    currentProcessedBitmap = bitmap
                    processedView.setImageBitmap(bitmap)
                }
            },
            onStatus = { message -> runOnUiThread { statusView.text = message } }
        )
        restoreAvatar()
        if (hasCameraPermission()) updateControls()
        else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun restoreAvatar() {
        val reference = currentAvatarUri() ?: liveSessionStore.reference
        if (reference.isNullOrBlank()) return
        saveAvatarUri(reference)
        streamingReferenceLock.select(reference)
        avatarPreview.setImageURI(Uri.parse(reference))
        avatarPreview.visibility = View.VISIBLE
        faceSwapEngine.setAvatar(reference)
        prepareAvatarEngine()
    }

    private fun prepareAvatarEngine() {
        Thread {
            val state = faceSwapEngine.prepareAvatar()
            runOnUiThread {
                statusView.text = when (state) {
                    LiveFaceEngineState.Ready -> "Deep-Live-Cam source face ready"
                    is LiveFaceEngineState.Fallback -> state.reason
                    else -> "Connecting to Deep-Live-Cam…"
                }
                updateControls()
            }
        }.start()
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
        previewView = androidx.camera.view.PreviewView(this).apply {
            implementationMode = androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
        container.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        processedView = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            scaleX = -1f
            setBackgroundColor(0xFF000000.toInt())
        }
        container.addView(processedView, FrameLayout.LayoutParams(-1, -1))
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))

        avatarPreview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF181818.toInt())
        }
        root.addView(avatarPreview, LinearLayout.LayoutParams(140, 140).apply {
            gravity = Gravity.CENTER
            setMargins(16, 8, 16, 4)
        })
        selectAvatarButton = Button(this).apply {
            text = "Choose source photo"
            setOnClickListener { pickAvatar.launch("image/*") }
        }
        root.addView(selectAvatarButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 4, 16, 4) })

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            text = "Camera ready"
            setPadding(16, 8, 16, 8)
        }
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 4, 16, 8)
        }
        startButton = Button(this).apply { text = "Start Live"; setOnClickListener { startSession() } }
        stopButton = Button(this).apply { text = "Stop"; setOnClickListener { stopSession() } }
        controls.addView(startButton)
        controls.addView(stopButton)
        root.addView(controls)

        voiceStatusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            text = "Voice: local recording/import"
        }
        root.addView(voiceStatusView)
        val voiceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 12)
        }
        recordVoiceButton = Button(this).apply { text = "Record Voice"; setOnClickListener { toggleRecording() } }
        val importVoiceButton = Button(this).apply { text = "Import Voice"; setOnClickListener { pickVoice.launch(arrayOf("audio/*")) } }
        playVoiceButton = Button(this).apply { text = "Play"; isEnabled = false; setOnClickListener { currentVoiceFile?.let(voiceController::play) } }
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
        val reference = currentAvatarUri() ?: return
        if (!faceSwapEngine.isReady) {
            prepareAvatarEngine()
            return
        }
        if (!streamingReferenceLock.select(reference) || !streamingReferenceLock.beginStreaming()) return
        liveSessionStore.markActive(reference)
        previewView.visibility = View.VISIBLE
        processedView.visibility = View.VISIBLE
        statusView.text = "Starting Deep-Live-Cam…"
        liveCameraController.start()
        updateControls()
    }

    private fun stopSession() {
        liveCameraController.stop()
        liveSessionStore.clear()
        streamingReferenceLock.stopStreaming()
        currentProcessedBitmap?.recycle()
        currentProcessedBitmap = null
        processedView.setImageDrawable(null)
        processedView.visibility = View.GONE
        statusView.text = "Live stopped — source photo kept"
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
        voiceController.startRecordingAndRemember(voiceAssetStore.newRecordingFile())
        recordVoiceButton.text = "Stop Recording"
        voiceStatusView.text = "Recording locally"
    }

    private fun updateControls() {
        val running = liveSessionStore.isActive
        val hasAvatar = currentAvatarUri() != null
        selectAvatarButton.isEnabled = !running
        startButton.isEnabled = hasCameraPermission() && hasAvatar && faceSwapEngine.isReady && !running
        stopButton.isEnabled = running
        if (!running && faceSwapEngine.state !is LiveFaceEngineState.Preparing) {
            statusView.text = when {
                !hasCameraPermission() -> "Camera permission required"
                !hasAvatar -> "Choose an avatar to begin"
                faceSwapEngine.state is LiveFaceEngineState.Fallback -> (faceSwapEngine.state as LiveFaceEngineState.Fallback).reason
                else -> "Deep-Live-Cam source ready"
            }
        }
    }

    private fun currentAvatarUri(): String? = getPreferences(MODE_PRIVATE).getString("avatar_uri", null)
    private fun saveAvatarUri(uri: String) { getPreferences(MODE_PRIVATE).edit().putString("avatar_uri", uri).apply() }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onUserLeaveHint() {
        // Release the real camera before another app requests it. Android does not
        // provide ordinary third-party apps with a virtual-camera injection API for WhatsApp.
        if (liveSessionStore.isActive) stopSession()
        (application as PrivacyApplication).lock()
        super.onUserLeaveHint()
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) (application as PrivacyApplication).lock()
        if (::liveCameraController.isInitialized) liveCameraController.close()
        currentProcessedBitmap?.recycle()
        currentProcessedBitmap = null
        faceSwapEngine.close()
        voiceController.close()
        super.onDestroy()
    }
}
