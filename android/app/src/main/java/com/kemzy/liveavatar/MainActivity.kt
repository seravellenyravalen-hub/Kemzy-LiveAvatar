package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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
    private lateinit var processedPreview: ImageView
    private lateinit var sourceThumbnail: ImageView
    private lateinit var status: TextView
    private lateinit var outputStatus: TextView
    private lateinit var selectSourceButton: Button
    private lateinit var voiceModeButton: Button
    private lateinit var startSwapButton: Button
    private lateinit var stopSwapButton: Button
    private lateinit var backgroundLiveButton: Button
    private lateinit var stopBackgroundLiveButton: Button
    private lateinit var cameraExecutor: ExecutorService

    private val framePipeline = FramePipeline(capacity = 1)
    private val sourceRepository by lazy { SourceFaceRepository(this) }
    private val modelRepository by lazy { ModelRepository(this) }
    private val modelBundleRepository by lazy { ModelBundleRepository(modelRepository) }
    private var swapRuntime: OnDeviceSwapRuntime? = null
    private var liveSwapEnabled = false
    private var voiceModeIndex = 0
    private val voiceModes = VoiceEffectProcessor.Mode.values()
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "Camera permission is required for Live mode."
    }
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startBackgroundLiveNow() else status.text = "Microphone permission is required for voice processing."
    }
    private val sourcePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(stream) { "Unable to open selected image" }
                requireNotNull(BitmapFactory.decodeStream(stream)) { "Selected file is not a readable image" }
            }
        }.onSuccess {
            contentResolver.openInputStream(uri).use { stream ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(stream))
                sourceRepository.setSource(bitmap)
                sourceThumbnail.setImageBitmap(bitmap)
                status.text = "Source face selected"
            }
        }.onFailure { error -> status.text = "Source image error: ${error.message ?: "unable to import image"}" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.cameraPreview)
        processedPreview = findViewById(R.id.processedPreview)
        sourceThumbnail = findViewById(R.id.sourceThumbnail)
        status = findViewById(R.id.statusText)
        outputStatus = findViewById(R.id.outputStatusText)
        selectSourceButton = findViewById(R.id.selectSourceButton)
        voiceModeButton = findViewById(R.id.voiceModeButton)
        startSwapButton = findViewById(R.id.startSwapButton)
        stopSwapButton = findViewById(R.id.stopSwapButton)
        backgroundLiveButton = findViewById(R.id.backgroundLiveButton)
        stopBackgroundLiveButton = findViewById(R.id.stopBackgroundLiveButton)
        cameraExecutor = Executors.newSingleThreadExecutor()

        selectSourceButton.setOnClickListener { sourcePicker.launch("image/*") }
        voiceModeButton.setOnClickListener { cycleVoiceMode() }
        startSwapButton.setOnClickListener { startLiveSwap() }
        stopSwapButton.setOnClickListener { stopLiveSwap() }
        backgroundLiveButton.setOnClickListener { startBackgroundLive() }
        stopBackgroundLiveButton.setOnClickListener { stopBackgroundLive() }
        voiceModeButton.text = "Voice: ${voiceModes[voiceModeIndex].name.replace('_', ' ')}"
        sourceRepository.loadSource()?.let { sourceThumbnail.setImageBitmap(it) }
        refreshOutputStatus()
    }

    private fun cycleVoiceMode() {
        voiceModeIndex = (voiceModeIndex + 1) % voiceModes.size
        val mode = voiceModes[voiceModeIndex]
        LiveCameraService.selectedVoiceMode = mode
        voiceModeButton.text = "Voice: ${mode.name.replace('_', ' ')}"
        if (LiveCameraService.active) {
            startService(Intent(this, LiveCameraService::class.java).apply {
                action = LiveCameraService.ACTION_SET_VOICE_MODE
                putExtra(LiveCameraService.EXTRA_VOICE_MODE, mode.name)
            })
            status.text = "Voice effect changed to ${mode.name.lowercase()}"
        }
    }

    override fun onResume() {
        super.onResume()
        if (appLock.isLocked()) { lockLauncher.launch(Intent(this, LockActivity::class.java)); return }
        refreshOutputStatus()
        if (LiveCameraService.active) {
            backgroundLiveButton.visibility = Button.GONE
            stopBackgroundLiveButton.visibility = Button.VISIBLE
            status.text = "Background Live is active"
            return
        }
        backgroundLiveButton.visibility = Button.VISIBLE
        stopBackgroundLiveButton.visibility = Button.GONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        if (!LiveCameraService.active) stopCamera()
        appLock.lock()
        super.onPause()
    }

    override fun onDestroy() {
        if (!LiveCameraService.active) stopCamera()
        swapRuntime?.close()
        swapRuntime = null
        cameraExecutor.shutdownNow()
        framePipeline.clear()
        super.onDestroy()
    }

    private fun startLiveSwap() {
        if (!sourceRepository.hasSource()) { status.text = "Select a source face first"; return }
        val bundle = modelBundleRepository.inspect()
        if (!bundle.isComplete) { status.text = modelBundleRepository.status(); return }
        swapRuntime?.close()
        swapRuntime = OnDeviceSwapRuntime(bundle)
        liveSwapEnabled = true
        processedPreview.visibility = ImageView.VISIBLE
        startSwapButton.visibility = Button.GONE
        stopSwapButton.visibility = Button.VISIBLE
        status.text = "Starting real Deep-Live-Cam model pipeline…"
        startCamera()
    }

    private fun stopLiveSwap() {
        liveSwapEnabled = false
        swapRuntime?.close(); swapRuntime = null
        processedPreview.setImageDrawable(null)
        processedPreview.visibility = ImageView.GONE
        startSwapButton.visibility = Button.VISIBLE
        stopSwapButton.visibility = Button.GONE
        status.text = "Live Swap stopped"
        startCamera()
    }

    private fun startBackgroundLive() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { permissionLauncher.launch(Manifest.permission.CAMERA); return }
        if (!sourceRepository.hasSource()) { status.text = "Select a source face first"; return }
        if (!modelBundleRepository.inspect().isComplete) { status.text = modelBundleRepository.status(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        startBackgroundLiveNow()
    }

    private fun startBackgroundLiveNow() {
        stopCamera()
        ContextCompat.startForegroundService(this, Intent(this, LiveCameraService::class.java).apply { action = LiveCameraService.ACTION_START })
        backgroundLiveButton.visibility = Button.GONE
        stopBackgroundLiveButton.visibility = Button.VISIBLE
        status.text = "Starting Background Live Swap + Voice Effects…"
    }

    private fun stopBackgroundLive() {
        startService(Intent(this, LiveCameraService::class.java).apply { action = LiveCameraService.ACTION_STOP })
        backgroundLiveButton.visibility = Button.VISIBLE
        stopBackgroundLiveButton.visibility = Button.GONE
        status.text = "Background Live stopped"
        refreshOutputStatus(); startCamera()
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
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { useCase ->
                useCase.setAnalyzer(cameraExecutor, if (liveSwapEnabled) {
                    val runtime = requireNotNull(swapRuntime)
                    LiveSwapAnalyzer(
                        runtime = runtime,
                        sourceProvider = { sourceRepository.loadSource() },
                        onProcessed = { bitmap -> runOnUiThread {
                            if (!isFinishing && !isDestroyed) { processedPreview.setImageBitmap(bitmap); status.text = "Live Swap active · real ONNX processing" }
                        } },
                        onFrameDropped = { },
                        onFrameError = { error -> runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Live Swap error: ${error.message ?: "model processing failed"}" } }
                    )
                } else {
                    LiveFrameAnalyzer(
                        pipeline = framePipeline,
                        onFrameAccepted = { runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Live camera ready · frames flowing" } },
                        onFrameDropped = { },
                        onFrameError = { error -> runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Frame processing error: ${error.message ?: "unsupported frame"}" } }
                    )
                })
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            status.text = if (liveSwapEnabled) "Live Swap ready" else "Live camera ready"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        framePipeline.clear()
    }
}
