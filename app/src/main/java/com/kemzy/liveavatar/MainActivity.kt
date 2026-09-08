package com.kemzy.liveavatar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val sessionController = SessionController()
    private val avatarSelection = AvatarSelection()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private lateinit var faceTracker: FaceTracker
    private lateinit var previewView: PreviewView
    private lateinit var avatarPreview: ImageView
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var selectAvatarButton: Button
    private var cameraProvider: ProcessCameraProvider? = null

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            avatarSelection.select(uri.toString())
            avatarPreview.setImageURI(uri)
            avatarPreview.visibility = View.VISIBLE
            statusView.text = "Avatar selected — press Start"
            updateControls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        faceTracker = FaceTracker(
            onResult = { result ->
                runOnUiThread { updateTrackingStatus(result) }
            },
            onError = { error ->
                runOnUiThread { statusView.text = "Face tracking error: ${error.message ?: "unknown"}" }
            }
        )
        buildUi()

        if (hasCameraPermission()) {
            updateControls()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        val header = TextView(this).apply {
            text = "Kemzy-LiveAvatar"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(16, 18, 16, 10)
        }
        root.addView(header)

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(
            previewView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        avatarPreview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Selected avatar"
            setBackgroundColor(0xFF181818.toInt())
        }
        root.addView(
            avatarPreview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                180
            ).apply {
                setMargins(16, 10, 16, 4)
            }
        )

        selectAvatarButton = Button(this).apply {
            text = "Choose Avatar"
            setOnClickListener { pickAvatar.launch("image/*") }
        }
        root.addView(
            selectAvatarButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 4, 16, 4)
            }
        )

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            text = "Camera ready"
            setPadding(16, 12, 16, 12)
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 4, 16, 24)
        }

        startButton = Button(this).apply {
            text = "Start"
            setOnClickListener { startSession() }
        }
        stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopSession() }
        }

        controls.addView(startButton)
        controls.addView(stopButton)
        root.addView(controls)

        setContentView(root)
    }

    private fun startSession() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        if (avatarSelection.uri == null) {
            statusView.text = "Choose an avatar first"
            return
        }

        sessionController.start()
        bindFrontCamera()
        updateControls()
    }

    private fun stopSession() {
        sessionController.stop()
        cameraProvider?.unbindAll()
        updateControls()
    }

    private fun bindFrontCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(analysisExecutor) { image -> faceTracker.process(image) }
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateTrackingStatus(result: FaceTrackingResult) {
        if (sessionController.state !is SessionState.Running) return
        statusView.text = if (result.faceCount == 0) {
            "Live session — no face detected"
        } else {
            "Face tracked • yaw %.0f° • pitch %.0f° • roll %.0f°".format(
                result.yawDegrees,
                result.pitchDegrees,
                result.rollDegrees
            )
        }
    }

    private fun updateControls() {
        val running = sessionController.state is SessionState.Running
        val hasAvatar = avatarSelection.uri != null
        selectAvatarButton.isEnabled = !running
        startButton.isEnabled = hasCameraPermission() && hasAvatar && !running
        stopButton.isEnabled = running
        statusView.text = when {
            running -> "Live session running"
            !hasCameraPermission() -> "Camera permission required"
            !hasAvatar -> "Choose an avatar to begin"
            else -> "Avatar ready — press Start"
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            updateControls()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        faceTracker.close()
        analysisExecutor.shutdown()
        sessionController.stop()
        super.onDestroy()
    }

    private companion object {
        const val CAMERA_PERMISSION_REQUEST = 1001
    }
}
