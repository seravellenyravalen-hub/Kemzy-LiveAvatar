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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var referenceImageStore: ReferenceImageStore
    private lateinit var liveSessionStore: LiveSessionStore
    private lateinit var previewView: PreviewView
    private lateinit var liveAvatarView: ImageView
    private lateinit var avatarThumbnail: ImageView
    private lateinit var videoView: VideoView
    private lateinit var statusView: TextView
    private lateinit var recordButton: Button
    private lateinit var playButton: Button
    private lateinit var importVideoButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var cameraController: LiveCameraController? = null
    private var lastPlaybackUri: Uri? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || liveSessionStore.isActive) return@registerForActivityResult
        runCatching {
            val previous = currentAvatarUri()
            val localUri = referenceImageStore.persist(uri)
            previous?.let(referenceImageStore::delete)
            saveAvatarUri(localUri)
            avatarThumbnail.setImageURI(Uri.parse(localUri))
            avatarThumbnail.visibility = View.VISIBLE
            cameraController?.setAvatar(localUri)
            prepareAvatar(localUri)
            updateControls()
        }.onFailure {
            statusView.text = "Avatar import failed"
        }
    }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lastPlaybackUri = uri
        videoView.setVideoURI(uri)
        videoView.visibility = View.VISIBLE
        playButton.isEnabled = true
        statusView.text = "Video imported — tap Play"
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            bindVisibleCamera()
            updateControls()
        } else {
            statusView.text = "Camera permission is required. Open Settings to enable it."
        }
    }

    private val requestMicrophonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecordingNow() else statusView.text = "Microphone permission is required for video audio."
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
        buildUi()
        createCameraController()
        restoreAvatar()
        if (hasCameraPermission()) bindVisibleCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onStart() {
        super.onStart()
        if (::liveSessionStore.isInitialized && liveSessionStore.isActive) {
            stopService(Intent(this, AvatarStreamingService::class.java))
            bindVisibleCamera()
        }
    }

    override fun onStop() {
        if (::liveSessionStore.isInitialized && liveSessionStore.isActive && !isFinishing) {
            cameraController?.setLiveEnabled(false)
            cameraController?.pauseCamera()
            val reference = currentAvatarUri()
            if (!reference.isNullOrBlank()) {
                startService(
                    Intent(this, AvatarStreamingService::class.java)
                        .setAction(AvatarStreamingService.ACTION_ENABLE_BACKGROUND_CAMERA)
                        .putExtra(AvatarStreamingService.EXTRA_REFERENCE, reference)
                )
            }
        }
        super.onStop()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 8)
        }
        header.addView(TextView(this).apply {
            text = "Kemzy-LiveAvatar"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 21f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        header.addView(ImageButton(this).apply {
            contentDescription = "Camera and microphone settings"
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFFFFFFF.toInt())
            setOnClickListener { startActivity(Intent(this@MainActivity, CameraSettingsActivity::class.java)) }
        }, LinearLayout.LayoutParams(52, 52))
        root.addView(header)

        val liveContainer = FrameLayout(this)
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        liveContainer.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        liveAvatarView = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        liveContainer.addView(liveAvatarView, FrameLayout.LayoutParams(-1, -1))

        videoView = VideoView(this).apply {
            visibility = View.GONE
            setBackgroundColor(0xFF000000.toInt())
        }
        liveContainer.addView(videoView, FrameLayout.LayoutParams(-1, -1))
        root.addView(liveContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        val avatarRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 4)
        }
        avatarThumbnail = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF1C1C1C.toInt())
            contentDescription = "Selected avatar"
        }
        avatarRow.addView(avatarThumbnail, LinearLayout.LayoutParams(64, 64))
        avatarRow.addView(Button(this).apply {
            text = "Import Avatar"
            setOnClickListener { pickAvatar.launch("image/*") }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(12, 0, 0, 0) })
        root.addView(avatarRow)

        statusView = TextView(this).apply {
            text = "Camera starting…"
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 6, 16, 6)
        }
        root.addView(statusView)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(10, 4, 10, 10)
        }
        startButton = Button(this).apply { text = "Start Live"; setOnClickListener { startSession() } }
        stopButton = Button(this).apply { text = "Stop"; setOnClickListener { stopSession() } }
        recordButton = Button(this).apply { text = "Record"; setOnClickListener { toggleRecording() } }
        controls.addView(startButton)
        controls.addView(stopButton)
        controls.addView(recordButton)
        root.addView(controls)

        val mediaControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 10)
        }
        importVideoButton = Button(this).apply { text = "Import"; setOnClickListener { pickVideo.launch(arrayOf("video/*")) } }
        playButton = Button(this).apply {
            text = "Play"
            isEnabled = false
            setOnClickListener { lastPlaybackUri?.let { videoView.setVideoURI(it); videoView.start(); videoView.visibility = View.VISIBLE } }
        }
        mediaControls.addView(importVideoButton)
        mediaControls.addView(playButton)
        root.addView(mediaControls)
        setContentView(root)
    }

    private fun createCameraController() {
        cameraController = LiveCameraController(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            onLiveFrame = { bitmap ->
                runOnUiThread {
                    val old = liveAvatarView.drawable
                    liveAvatarView.setImageBitmap(bitmap)
                    old?.let { if (it is android.graphics.drawable.BitmapDrawable) it.bitmap?.recycle() }
                }
            },
            onRecordingFinalized = { uri ->
                runOnUiThread {
                    recordButton.text = "Record"
                    recordButton.isEnabled = true
                    lastPlaybackUri = uri
                    if (uri != null) {
                        videoView.setVideoURI(uri)
                        playButton.isEnabled = true
                        statusView.text = "Recording saved — tap Play"
                    }
                }
            },
            onError = { message -> runOnUiThread { statusView.text = message; recordButton.isEnabled = true } }
        )
    }

    private fun restoreAvatar() {
        val uri = currentAvatarUri() ?: return
        avatarThumbnail.setImageURI(Uri.parse(uri))
        avatarThumbnail.visibility = View.VISIBLE
        cameraController?.setAvatar(uri)
        prepareAvatar(uri)
    }

    private fun prepareAvatar(uri: String) {
        cameraController?.prepareAvatarAsync { state ->
            statusView.text = when (state) {
                is LiveFaceEngineState.Ready -> "Avatar ready — Start Live"
                is LiveFaceEngineState.Preparing -> "Preparing live avatar…"
                is LiveFaceEngineState.Fallback -> state.reason
                LiveFaceEngineState.Idle -> "Choose an avatar to begin"
            }
            updateControls()
        }
    }

    private fun bindVisibleCamera() {
        if (!hasCameraPermission()) return
        cameraController?.bindCamera()
        cameraController?.setLiveEnabled(liveSessionStore.isActive)
        previewView.visibility = View.VISIBLE
        if (!liveSessionStore.isActive) liveAvatarView.visibility = View.GONE
        statusView.text = if (liveSessionStore.isActive) "Live camera active" else "Camera ready"
    }

    private fun startSession() {
        if (!hasCameraPermission()) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val reference = currentAvatarUri() ?: run {
            statusView.text = "Import an avatar first"
            return
        }
        if (cameraController?.isReady != true) {
            prepareAvatar(reference)
            statusView.text = "Preparing live avatar…"
            return
        }
        liveSessionStore.markActive(reference)
        cameraController?.setLiveEnabled(true)
        // Arm the camera foreground service while the Activity is visible. Android requires
        // camera foreground services to be created while the app has a visible Activity.
        ContextCompat.startForegroundService(
            this,
            Intent(this, AvatarStreamingService::class.java)
                .setAction(AvatarStreamingService.ACTION_ARM)
                .putExtra(AvatarStreamingService.EXTRA_REFERENCE, reference)
        )
        liveAvatarView.visibility = View.VISIBLE
        videoView.visibility = View.GONE
        previewView.visibility = View.GONE
        statusView.text = "Live avatar active"
        startButton.isEnabled = false
        stopButton.isEnabled = true
        recordButton.isEnabled = true
    }

    private fun stopSession() {
        cameraController?.setLiveEnabled(false)
        cameraController?.stopRecording()
        stopService(Intent(this, AvatarStreamingService::class.java).setAction(AvatarStreamingService.ACTION_STOP))
        liveSessionStore.clear()
        liveAvatarView.setImageDrawable(null)
        liveAvatarView.visibility = View.GONE
        videoView.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        updateControls()
    }

    private fun toggleRecording() {
        val controller = cameraController ?: return
        if (controller.isRecording) {
            controller.stopRecording()
            recordButton.text = "Finalizing…"
            recordButton.isEnabled = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingNow()
        } else {
            requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingNow() {
        videoView.visibility = View.GONE
        cameraController?.startRecording()
        recordButton.text = "Stop Recording"
        recordButton.isEnabled = true
        statusView.text = "Recording video + microphone audio"
    }

    private fun updateControls() {
        val running = liveSessionStore.isActive
        val hasAvatar = currentAvatarUri() != null
        startButton.isEnabled = hasAvatar && cameraController?.isReady == true && !running
        stopButton.isEnabled = running
        recordButton.isEnabled = running
        importVideoButton.isEnabled = !running
        if (!running && hasAvatar && cameraController?.isReady == true) statusView.text = "Avatar ready — Start Live"
    }

    private fun currentAvatarUri(): String? = getPreferences(MODE_PRIVATE).getString("avatar_uri", null)

    private fun saveAvatarUri(uri: String) {
        getPreferences(MODE_PRIVATE).edit().putString("avatar_uri", uri).apply()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        if (isFinishing) {
            stopService(Intent(this, AvatarStreamingService::class.java).setAction(AvatarStreamingService.ACTION_STOP))
            if (::liveSessionStore.isInitialized) liveSessionStore.clear()
            (application as PrivacyApplication).lock()
        }
        cameraController?.release()
        super.onDestroy()
    }
}
