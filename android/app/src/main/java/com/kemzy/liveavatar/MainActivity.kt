package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
    private lateinit var outputStatus: TextView
    private lateinit var backgroundLiveButton: Button
    private lateinit var stopBackgroundLiveButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private val framePipeline = FramePipeline(capacity = 1)
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) finish()
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
        outputStatus = findViewById(R.id.outputStatusText)
        backgroundLiveButton = findViewById(R.id.backgroundLiveButton)
        stopBackgroundLiveButton = findViewById(R.id.stopBackgroundLiveButton)
        cameraExecutor = Executors.newSingleThreadExecutor()

        backgroundLiveButton.setOnClickListener { startBackgroundLive() }
        stopBackgroundLiveButton.setOnClickListener { stopBackgroundLive() }
        refreshOutputStatus()
    }

    override fun onResume() {
        super.onResume()
        if (appLock.isLocked()) {
            lockLauncher.launch(Intent(this, LockActivity::class.java))
            return
        }

        refreshOutputStatus()
        if (LiveCameraService.active) {
            backgroundLiveButton.visibility = Button.GONE
            stopBackgroundLiveButton.visibility = Button.VISIBLE
            status.text = "Background Live is active"
            return
        }

        backgroundLiveButton.visibility = Button.VISIBLE
        stopBackgroundLiveButton.visibility = Button.GONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        // A running background session intentionally owns the camera after the
        // activity leaves the foreground. Otherwise release the preview camera.
        if (!LiveCameraService.active) stopCamera()
        appLock.lock()
        super.onPause()
    }

    override fun onDestroy() {
        if (!LiveCameraService.active) stopCamera()
        cameraExecutor.shutdownNow()
        framePipeline.clear()
        super.onDestroy()
    }

    private fun startBackgroundLive() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        stopCamera()
        val intent = Intent(this, LiveCameraService::class.java).apply {
            action = LiveCameraService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        backgroundLiveButton.visibility = Button.GONE
        stopBackgroundLiveButton.visibility = Button.VISIBLE
        status.text = "Starting Background Live…"
    }

    private fun stopBackgroundLive() {
        val intent = Intent(this, LiveCameraService::class.java).apply {
            action = LiveCameraService.ACTION_STOP
        }
        startService(intent)
        backgroundLiveButton.visibility = Button.VISIBLE
        stopBackgroundLiveButton.visibility = Button.GONE
        status.text = "Background Live stopped"
        refreshOutputStatus()
        startCamera()
    }

    private fun refreshOutputStatus() {
        val report = CameraCapability.inspect(this)
        val route = report.bestRoute()
        val virtual = when (report.virtualCameraSupported) {
            true -> "native virtual-camera support reported"
            false -> "virtual-camera API reports unavailable"
            null -> if (report.virtualCameraApiPresent) "virtual-camera API detected but support is unknown" else "virtual-camera API not exposed"
        }
        outputStatus.text = "Camera output: ${route.name} · $virtual · external camera=${report.externalCameraPresent}"
    }

    private fun startCamera() {
        if (LiveCameraService.active) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed || appLock.isLocked() || LiveCameraService.active) return@addListener

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
                                    if (!isFinishing && !isDestroyed) {
                                        status.text = "Live camera ready · frames flowing"
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
            status.text = "Live camera ready"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        }
        framePipeline.clear()
    }
}
