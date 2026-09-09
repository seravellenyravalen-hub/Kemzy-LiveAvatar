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
    private val voiceSessionPolicy = VoiceSessionPolicy()
    private lateinit var referenceImageStore: ReferenceImageStore
    private lateinit var liveSessionStore: LiveSessionStore
    private lateinit var faceSwapEngine: FaceSwapEngine
    private lateinit var voiceAssetStore: VoiceAssetStore
    private lateinit var voiceController: LocalVoiceController
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var trackingAvatarView: ImageView
    private lateinit var avatarPreview: ImageView
    private lateinit var statusView: TextView
    private lateinit var voiceStatusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var selectAvatarButton: Button
    private lateinit var recordVoiceButton: Button
    private lateinit var importVoiceButton: Button
    private lateinit var playVoiceButton: Button
    private var currentVoiceFile: File? = null
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
        }
    }

    private val pickVoice = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || liveSessionStore.isActive) return@registerForActivityResult
        runCatching {
            if (!voiceSessionPolicy.select(VoiceSourceType.IMPORTED_FILE)) return@runCatching
            currentVoiceFile = voiceAssetStore.import(uri)
            voiceStatusView.text = "Voice imported locally"
            playVoiceButton.isEnabled = true
            voiceSessionPolicy.stopStreaming()
        }.onFailure {
            voiceStatusView.text = "Voice import unavailable"
            voiceSessionPolicy.stopStreaming()
        }
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
        referenceImageStore = ReferenceImageStore(applicationContext)
        liveSessionStore = LiveSessionStore(applicationContext)
        voiceAssetStore = VoiceAssetStore(applicationContext)
        voiceController = LocalVoiceController(applicationContext)
        faceSwapEngine = OnDeviceFaceSwapEngine(applicationContext)
        buildUi()
        restoreSessionAndAvatar()
        if (hasCameraPermission()) updateControls() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onStart() {
        super.onStart()
        uiHandler.post(framePoller)
    }

    override fun onStop() {
        uiHandler.removeCallbacks(framePoller)
        super.onStop()
    }

    private val framePoller = object : Runnable {
        override fun run() {
            if (liveSessionStore.isActive) {
                StreamingFrameBus.take()?.let { frame ->
                    trackingAvatarView.setImageBitmap(frame)
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
        if (liveSessionStore.isActive && !streamingReferenceLock.isStreaming) {
            streamingReferenceLock.beginStreaming()
            previewView.visibility = View.INVISIBLE
            trackingAvatarView.visibility = View.VISIBLE
        }
    }

    private fun prepareAvatarEngine() {
        Thread {
            faceSwapEngine.prepareAvatar()
            runOnUiThread { updateControls() }
        }.start()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
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
        trackingAvatarView = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        container.addView(trackingAvatarView, FrameLayout.LayoutParams(-1, -1))
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))

        avatarPreview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF181818.toInt())
        }
        root.addView(avatarPreview, LinearLayout.LayoutParams(-1, 180).apply { setMargins(16, 10, 16, 4) })

        selectAvatarButton = Button(this).apply {
            text = "Choose Avatar"
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
        startButton = Button(this).apply { text = "Start"; setOnClickListener { startSession() } }
        stopButton = Button(this).apply { text = "Stop"; setOnClickListener { stopSession() } }
        controls.addView(startButton)
        controls.addView(stopButton)
        root.addView(controls)

        voiceStatusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            text = "Voice: local recording/import"
            setPadding(16, 4, 16, 4)
        }
        root.addView(voiceStatusView)
        val voiceControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 12)
        }
        recordVoiceButton = Button(this).apply { text = "Record Voice"; setOnClickListener { toggleRecording() } }
        importVoiceButton = Button(this).apply { text = "Import Voice"; setOnClickListener { pickVoice.launch(arrayOf("audio/*")) } }
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
        val intent = Intent(this, AvatarStreamingService::class.java)
            .putExtra(AvatarStreamingService.EXTRA_REFERENCE, reference)
        ContextCompat.startForegroundService(this, intent)
        previewView.visibility = View.INVISIBLE
        trackingAvatarView.visibility = View.VISIBLE
        trackingAvatarView.setImageDrawable(null)
        statusView.text = "Live avatar active"
        updateControls()
    }

    private fun stopSession() {
        startService(Intent(this, AvatarStreamingService::class.java).setAction(AvatarStreamingService.ACTION_STOP))
        liveSessionStore.clear()
        streamingReferenceLock.stopStreaming()
        currentAvatarUri()?.let(referenceImageStore::delete)
        clearAvatarUri()
        trackingAvatarView.setImageDrawable(null)
        trackingAvatarView.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        voiceSessionPolicy.stopStreaming()
        updateControls()
    }

    private fun toggleRecording() {
        if (liveSessionStore.isActive) return
        if (voiceController.isRecording) {
            val file = voiceController.stopRecording()
            currentVoiceFile = file
            recordVoiceButton.text = "Record Voice"
            voiceStatusView.text = if (file != null) "Voice saved locally" else "Recording failed"
            playVoiceButton.isEnabled = file?.isFile == true
            voiceSessionPolicy.stopStreaming()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else {
            requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecording() {
        if (liveSessionStore.isActive) return
        if (!voiceSessionPolicy.select(VoiceSourceType.MICROPHONE) || !voiceSessionPolicy.beginStreaming()) return
        runCatching {
            voiceController.startRecordingAndRemember(voiceAssetStore.newRecordingFile())
            recordVoiceButton.text = "Stop Recording"
            voiceStatusView.text = "Recording locally"
        }.onFailure {
            voiceSessionPolicy.stopStreaming()
            voiceStatusView.text = "Recording unavailable"
        }
    }

    private fun updateControls() {
        val running = liveSessionStore.isActive
        val recording = voiceController.isRecording
        val hasAvatar = currentAvatarUri() != null
        selectAvatarButton.isEnabled = !running
        startButton.isEnabled = hasCameraPermission() && hasAvatar && faceSwapEngine.isReady && !running
        stopButton.isEnabled = running
        recordVoiceButton.isEnabled = !running || recording
        importVoiceButton.isEnabled = !running
        if (!running) {
            statusView.text = when {
                !hasCameraPermission() -> "Camera permission required"
                !hasAvatar -> "Choose an avatar to begin"
                !faceSwapEngine.isReady -> "Preparing avatar"
                else -> "Reference ready"
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

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        faceSwapEngine.close()
        voiceController.close()
        super.onDestroy()
    }
}
