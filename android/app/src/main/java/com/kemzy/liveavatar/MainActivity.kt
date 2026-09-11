package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var cameraExecutor: ExecutorService
    private val framePipeline = FramePipeline<Frame>(capacity = 1)
    private val modelRepository by lazy { ModelRepository(this) }
    private var liveInferenceEngine: InferenceEngine? = null
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
        cameraExecutor = Executors.newSingleThreadExecutor()
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
        stopCamera()
        appLock.lock()
        super.onPause()
    }

    override fun onDestroy() {
        stopCamera()
        cameraExecutor.shutdownNow()
        framePipeline.clear()
        super.onDestroy()
    }

    private fun startCamera() {
        if (!prepareLiveInference()) return

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
            status.text = "Live camera ready · model ${liveInferenceEngine?.modelName() ?: "not loaded"}"
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Opens the installed ONNX graph directly from its file path. Do not call
     * readBytes() here: the model can be larger than the app's managed heap.
     */
    private fun prepareLiveInference(): Boolean {
        val modelFile: File = modelRepository.installedModels().firstOrNull()
            ?: run {
                liveInferenceEngine?.close()
                liveInferenceEngine = null
                status.text = "Live camera available · import a compatible .onnx model first"
                return true
            }

        if (liveInferenceEngine?.modelName() == modelFile.name) return true

        liveInferenceEngine?.close()
        return try {
            liveInferenceEngine = OnnxInferenceEngine(modelFile, modelFile.name)
            true
        } catch (error: Throwable) {
            liveInferenceEngine = null
            status.text = "Model load failed: ${error.message ?: "unsupported ONNX model"}"
            false
        }
    }

    private fun stopCamera() {
        runCatching {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        }
        framePipeline.clear()
        liveInferenceEngine?.close()
        liveInferenceEngine = null
    }
}
