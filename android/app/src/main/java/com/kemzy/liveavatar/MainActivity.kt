package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
import com.kemzy.liveavatar.camera.StudioFaceTracker
import com.kemzy.liveavatar.liveportrait.LivePortraitEngine
import com.kemzy.liveavatar.output.LiveOutput
import com.kemzy.liveavatar.output.MyCamBridge
import com.kemzy.liveavatar.output.RtmpLiveOutput
import com.kemzy.liveavatar.output.VirtualCameraBridge
import com.kemzy.liveavatar.studio.StudioProfile
import com.kemzy.liveavatar.studio.StudioProfileRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var processedPreview: ImageView
    private lateinit var status: TextView
    private lateinit var sourceText: TextView
    private lateinit var modelText: TextView
    private lateinit var rtmpEndpoint: EditText
    private lateinit var voiceButton: Button
    private lateinit var outputButton: Button
    private lateinit var liveButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var processingExecutor: ExecutorService

    private val framePipeline = FramePipeline(capacity = 1)
    private val modelRepository by lazy { ModelRepository(this) }
    private val bundleRepository by lazy { LiveModelBundleRepository(modelRepository) }
    private val modelImporter by lazy { LivePortraitModelImporter(modelRepository, contentResolver) }
    private val studio by lazy { StudioProfileRepository(this) }
    private val faceTracker by lazy { StudioFaceTracker() }
    private val appLock by lazy { (application as KemzyApplication).privacyLock }
    private val processing = AtomicBoolean(false)

    private var sourceBitmap: Bitmap? = null
    private var portraitEngine: LivePortraitEngine? = null
    private var selectedVoice = StudioProfile.VoiceMode.NORMAL
    private var selectedOutput = StudioProfile.OutputMode.PREVIEW
    private var myCam: VirtualCameraBridge = MyCamBridge()
    private var outputSink: LiveOutput? = null
    private var displayedBitmap: Bitmap? = null

    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sourceBitmap?.recycle()
            sourceBitmap = SourceFaceBitmapLoader(contentResolver).load(uri)
            studio.updateSource(uri.toString())
            sourceText.text = "Source: saved"
            status.text = "Source saved · validate models, then Start Live"
        }.onFailure { status.text = "Source error: ${it.message ?: "unable to load image"}" }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        runCatching {
            val result = modelImporter.importUris(uris)
            updateModelStatus()
            status.text = if (result.missing.isEmpty()) "LivePortrait bundle installed and validated"
            else "Models imported · missing: ${result.missing.joinToString()}"
        }.onFailure { status.text = "Model import failed: ${it.message ?: "unable to import bundle"}" }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else status.text = "Camera permission is required for Live mode."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.cameraPreview)
        processedPreview = findViewById(R.id.processedPreview)
        status = findViewById(R.id.statusText)
        sourceText = findViewById(R.id.sourceText)
        modelText = findViewById(R.id.modelText)
        rtmpEndpoint = findViewById(R.id.rtmpEndpoint)
        voiceButton = findViewById(R.id.voiceButton)
        outputButton = findViewById(R.id.outputButton)
        liveButton = findViewById(R.id.liveButton)
        stopButton = findViewById(R.id.stopButton)
        cameraExecutor = Executors.newSingleThreadExecutor()
        processingExecutor = Executors.newSingleThreadExecutor()

        val profile = studio.load()
        selectedVoice = profile.voiceMode
        selectedOutput = profile.outputMode
        rtmpEndpoint.setText(profile.rtmpEndpoint)
        voiceButton.text = "VOICE: ${selectedVoice.name}"
        outputButton.text = "OUTPUT: ${selectedOutput.name}"

        findViewById<Button>(R.id.selectSourceButton).setOnClickListener { sourcePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }
        findViewById<Button>(R.id.importModelsButton).setOnClickListener { modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*")) }
        voiceButton.setOnClickListener { cycleVoice() }
        outputButton.setOnClickListener { cycleOutput() }
        liveButton.setOnClickListener { startLive() }
        stopButton.setOnClickListener { stopLive() }

        restoreSource(profile.sourceUri)
        updateModelStatus()
    }

    private fun restoreSource(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            sourceBitmap?.recycle()
            sourceBitmap = SourceFaceBitmapLoader(contentResolver).load(Uri.parse(uriString))
            sourceText.text = "Source: restored"
        }.onFailure { sourceText.text = "Source: reselect required" }
    }

    private fun cycleVoice() {
        selectedVoice = StudioProfile.VoiceMode.values()[(selectedVoice.ordinal + 1) % StudioProfile.VoiceMode.values().size]
        voiceButton.text = "VOICE: ${selectedVoice.name}"
        studio.updateVoice(selectedVoice)
        status.text = if (selectedVoice == StudioProfile.VoiceMode.NORMAL) "Voice: microphone passthrough" else "Voice ${selectedVoice.name}: converter must be device-validated"
    }

    private fun cycleOutput() {
        selectedOutput = StudioProfile.OutputMode.values()[(selectedOutput.ordinal + 1) % StudioProfile.OutputMode.values().size]
        outputButton.text = "OUTPUT: ${selectedOutput.name}"
        studio.updateOutput(selectedOutput, rtmpEndpoint.text.toString().trim())
        status.text = "Output selected: ${selectedOutput.name}"
    }

    private fun updateModelStatus() {
        val bundle = bundleRepository.inspect()
        modelText.text = if (bundle.isComplete) "LivePortrait models: complete" else "LivePortrait models: missing ${bundle.missingRoles().joinToString()}"
    }

    private fun startLive() {
        val source = sourceBitmap ?: run { status.text = "Select a source portrait first."; return }
        if (selectedVoice != StudioProfile.VoiceMode.NORMAL) {
            status.text = "Live blocked: ${selectedVoice.name} voice conversion is not installed yet; NORMAL is the verified voice path."
            return
        }
        val bundle = bundleRepository.inspect()
        if (!bundle.isComplete) {
            status.text = "Live blocked · import all required LivePortrait models first"
            updateModelStatus(); return
        }
        runCatching {
            stopProcessingOnly()
            val sourceFace = faceTracker.largestFace(source) ?: error("No face detected in the selected source portrait")
            val engine = LivePortraitEngine(bundle)
            engine.initialize(source, sourceFace)
            portraitEngine = engine
            outputSink = when (selectedOutput) {
                StudioProfile.OutputMode.PREVIEW -> null
                StudioProfile.OutputMode.RTMP -> RtmpLiveOutput(this, rtmpEndpoint.text.toString().trim()) { message -> runOnUiThread { status.text = message } }
                StudioProfile.OutputMode.MYCAM -> null
            }
            if (selectedOutput == StudioProfile.OutputMode.MYCAM) {
                check(myCam.isAvailable) { "MyCam bridge is not connected on this device" }
                check(myCam.connect().isSuccess) { "Unable to connect to MyCam" }
            }
            outputSink?.start()
            processing.set(true)
            processedPreview.visibility = ImageView.VISIBLE
            processingExecutor.execute { processFrames() }
            studio.updateOutput(selectedOutput, rtmpEndpoint.text.toString().trim())
            status.text = "Kémzy Studio Live active · ${selectedOutput.name}"
        }.onFailure {
            stopLive()
            status.text = "Live start failed: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun processFrames() {
        while (processing.get() && !isFinishing && !isDestroyed) {
            val frame = framePipeline.poll() ?: run { Thread.sleep(5); continue }
            try {
                val face = faceTracker.largestFace(frame.bitmap)
                val output = if (face == null) null else portraitEngine?.process(frame.bitmap, face)
                if (output != null) {
                    outputSink?.submit(output)
                    if (selectedOutput == StudioProfile.OutputMode.MYCAM) myCam.submit(output)
                    showProcessedPreview(output)
                }
            } catch (error: Throwable) {
                runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "AI frame error: ${error.message ?: error.javaClass.simpleName}" }
            } finally {
                if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            }
        }
    }

    private fun showProcessedPreview(frame: Bitmap) {
        runOnUiThread {
            if (isFinishing || isDestroyed) { if (!frame.isRecycled) frame.recycle(); return@runOnUiThread }
            displayedBitmap?.let { if (it !== frame && !it.isRecycled) it.recycle() }
            displayedBitmap = frame
            processedPreview.setImageBitmap(frame)
        }
    }

    private fun stopProcessingOnly() {
        processing.set(false)
        framePipeline.clear()
        runCatching { outputSink?.close() }
        outputSink = null
        runCatching { myCam.disconnect() }
        runCatching { portraitEngine?.close() }
        portraitEngine = null
        displayedBitmap?.let { if (!it.isRecycled) it.recycle() }
        displayedBitmap = null
        processedPreview.setImageDrawable(null)
    }

    private fun stopLive() {
        stopProcessingOnly()
        processedPreview.visibility = ImageView.GONE
        status.text = "Live mode stopped"
    }

    override fun onResume() {
        super.onResume()
        if (appLock.isLocked()) { lockLauncher.launch(Intent(this, LockActivity::class.java)); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        stopLive(); stopCamera(); appLock.lock(); super.onPause()
    }

    override fun onDestroy() {
        stopLive(); stopCamera(); cameraExecutor.shutdownNow(); processingExecutor.shutdownNow(); faceTracker.close()
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }; sourceBitmap = null
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed || appLock.isLocked()) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { useCase ->
                useCase.setAnalyzer(cameraExecutor, LiveFrameAnalyzer(
                    pipeline = framePipeline,
                    onFrameError = { error -> runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Frame error: ${error.message ?: "unsupported frame"}" } }
                ))
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            status.text = "Camera ready · Studio setup loaded"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        framePipeline.clear()
    }
}
