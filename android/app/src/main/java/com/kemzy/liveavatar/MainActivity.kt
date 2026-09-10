package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var sourceText: TextView
    private lateinit var liveButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraExecutor: ExecutorService

    private val framePipeline = FramePipeline(capacity = 1)
    private val sourceFaces = SourceFaceRepository()
    private val liveState = LiveSessionState()
    private val liveController = LiveSessionController(liveState)
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }

    private val sourcePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        sourceFaces.select(uri.toString())
        liveState.selectSource(uri.toString())
        sourceText.text = "Source face: selected"
        status.text = "Source face ready · tap LIVE"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else status.text = "Camera permission is required for Live mode."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.cameraPreview)
        status = findViewById(R.id.statusText)
        sourceText = findViewById(R.id.sourceText)
        liveButton = findViewById(R.id.liveButton)
        stopButton = findViewById(R.id.stopButton)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.selectSourceButton).setOnClickListener {
            sourcePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        liveButton.setOnClickListener {
            runCatching {
                liveController.start()
                status.text = "Live mode active · AI swap engine requires a compatible model"
            }.onFailure { error ->
                status.text = error.message ?: "Select a source face first."
            }
        }
        stopButton.setOnClickListener {
            liveController.stop()
            status.text = "Live mode stopped"
        }
    }

    override fun onResume() {
        super.onResume()
        if (appLock.isLocked()) {
            lockLauncher.launch(Intent(this, LockActivity::class.java))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        liveController.stop()
        stopCamera()
        appLock.lock()
        super.onPause()
    }

    override fun onDestroy() {
        liveController.stop()
        stopCamera()
        cameraExecutor.shutdownNow()
        framePipeline.clear()
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed || appLock.isLocked()) return@addListener

            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(
                        cameraExecutor,
                        LiveFrameAnalyzer(
                            pipeline = framePipeline,
                            onFrameAccepted = {
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed && !liveController.isRunning) {
                                        status.text = "Camera ready · select a source face"
                                    }
                                }
                            },
                            onFrameDropped = {
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        status.text = "Live camera · reducing backlog"
                                    }
                                }
                            },
                            onFrameError = { error ->
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        status.text = "Frame processing error: ${error.message ?: "unsupported frame"}"
                                    }
                                }
                            }
                        )
                    )
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
            status.text = "Camera ready · select a source face"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        }
        framePipeline.clear()
    }
}
