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
            statusView.text = "Preparing on-device AI…"
            prepareAvatarEngine()
            updateControls()
        }.onFailure { statusView.text = "Avatar import failed" }
    }

    private val pickVoice = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            currentVoiceFile = voiceAssetStore.import(uri)
            voiceStatusView.text = "Voice imported locally"
            playVoiceButton.setEnabled(true)
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
        // Free path: the actual face-swap model runs locally through ONNX Runtime.
        faceSwapEngine = OnDeviceFaceSwapEngine(applicationContext)
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
                    LiveFaceEngineState.Ready -> "AI source face ready — camera can go live"
                    is LiveFaceEngineState.Fallback -> state.reason
                    else -> "Preparing on-device AI models…"
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
            text = "Select a source face"
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 6, 16, 6)
        }
        root.addView(statusView, LinearLayout.LayoutParams(-1, -2))

        startButton = Button(this).apply {
            text = "Start live"
            setEnabled(false)
            setOnClickListener { startSession() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 4, 16, 4) })

        stopButton = Button(this).apply {
            text = "Stop"
            setEnabled(false)
            setOnClickListener { stopSession() }
        }
        root.addView(stopButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 4, 16, 4) })

        voiceStatusView = TextView(this).apply {
            text = "Voice: not recorded"
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(voiceStatusView, LinearLayout.LayoutParams(-1, -2))

        recordVoiceButton = Button(this).apply {
            text = "Record voice"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (voiceController.isRecording) stopVoiceRecording() else startVoiceRecording()
                } else requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        root.addView(recordVoiceButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 4, 16, 2) })

        playVoiceButton = Button(this).apply {
            text = "Play voice"
            setEnabled(false)
            setOnClickListener { currentVoiceFile?.let { voiceController.play(it) } }
        }
        root.addView(playVoiceButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 2, 16, 10) })

        setContentView(root)
    }

    private fun startSession() {
        if (!hasCameraPermission()) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        if (faceSwapEngine.state !is LiveFaceEngineState.Ready) {
            statusView.text = "AI models are still preparing…"
            prepareAvatarEngine()
            return
        }
        val reference = currentAvatarUri()
        if (reference.isNullOrBlank()) {
            statusView.text = "Choose a source photo first"
            return
        }
        liveSessionStore.markActive(reference)
        processedView.visibility = View.VISIBLE
        liveCameraController.start()
        startButton.setEnabled(false)
        stopButton.setEnabled(true)
        statusView.text = "Live face swap running"
    }

    private fun stopSession() {
        liveCameraController.stop()
        liveSessionStore.clearActive()
        processedView.visibility = View.GONE
        stopButton.setEnabled(false)
        updateControls()
        statusView.text = "Live stopped"
    }

    private fun updateControls() {
        val ready = faceSwapEngine.state is LiveFaceEngineState.Ready
        val active = liveSessionStore.isActive
        startButton.setEnabled(hasCameraPermission() && ready && !active)
        stopButton.setEnabled(active)
    }

    private fun startVoiceRecording() {
        val output = voiceAssetStore.newRecordingFile()
        voiceController.startRecording(output)
        currentVoiceFile = output
        recordVoiceButton.text = "Stop voice"
        playVoiceButton.setEnabled(false)
        voiceStatusView.text = "Voice recording started — tap Stop voice to finish"
    }

    private fun stopVoiceRecording() {
        currentVoiceFile = voiceController.stopRecording() ?: currentVoiceFile
        recordVoiceButton.text = "Record voice"
        playVoiceButton.setEnabled(currentVoiceFile != null)
        voiceStatusView.text = if (currentVoiceFile != null) "Voice recording ready" else "Voice recording failed"
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun currentAvatarUri(): String? = liveSessionStore.reference

    private fun saveAvatarUri(uri: String) {
        liveSessionStore.setReference(uri)
    }

    override fun onUserLeaveHint() {
        if (::liveCameraController.isInitialized) liveCameraController.stop()
        liveSessionStore.clearActive()
        (application as PrivacyApplication).lock()
        super.onUserLeaveHint()
    }

    override fun onDestroy() {
        if (::liveCameraController.isInitialized) liveCameraController.stop()
        if (voiceController.isRecording) voiceController.stopRecording()
        currentProcessedBitmap?.recycle()
        faceSwapEngine.close()
        voiceController.close()
        super.onDestroy()
    }
}
