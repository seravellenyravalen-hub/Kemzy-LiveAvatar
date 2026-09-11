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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var processedPreview: ImageView
    private lateinit var status: TextView
    private lateinit var sourceText: TextView
    private lateinit var rtmpEndpoint: EditText
    private lateinit var liveButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var processingExecutor: ExecutorService

    private val framePipeline = FramePipeline(capacity = 1)
    private val previewBitmap = BitmapOwnershipSlot()
    private val sourceFaces = SourceFaceRepository()
    private val sourceLoader by lazy { SourceFaceBitmapLoader(contentResolver) }
    private val modelRepository by lazy { ModelRepository(this) }
    private val liveState = LiveSessionState()
    private val liveController = LiveSessionController(liveState)
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    private var sourceBitmap: Bitmap? = null
    private var swapProcessor: LiveSwapProcessor? = null
    private var detector: MlKitFaceDetector? = null
    private var embeddingEngine: OnnxInferenceEngine? = null
    private var swapperEngine: OnnxInferenceEngine? = null
    private var rtmpOutput: RtmpLiveOutput? = null
    private var pendingModelName: String? = null
    private val processing = AtomicBoolean(false)

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode != RESULT_OK) finish() }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sourceBitmap?.recycle()
            sourceBitmap = sourceLoader.load(uri)
            sourceFaces.select(uri.toString())
            liveState.selectSource(uri.toString())
            sourceText.text = "Source face: selected"
            status.text = "Source face ready · tap LIVE"
        }.onFailure { error -> status.text = "Source error: ${error.message ?: "unable to load image"}" }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val name = pendingModelName ?: return@registerForActivityResult
        try {
            modelRepository.importModel(contentResolver, uri, name)
            status.text = "$name imported · ready for Live"
        } catch (error: Throwable) {
            status.text = "Model import failed: ${error.message ?: "unable to import model"}"
        } finally {
            pendingModelName = null
        }
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
        rtmpEndpoint = findViewById(R.id.rtmpEndpoint)
        liveButton = findViewById(R.id.liveButton)
        stopButton = findViewById(R.id.stopButton)
        cameraExecutor = Executors.newSingleThreadExecutor()
        processingExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.selectSourceButton).setOnClickListener {
            sourcePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        }
        findViewById<Button>(R.id.importArcFaceButton).setOnClickListener {
            pendingModelName = "arcface_112.onnx"
            modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }
        findViewById<Button>(R.id.importInswapperButton).setOnClickListener {
            pendingModelName = "inswapper_128_fp16.onnx"
            modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }
        liveButton.setOnClickListener { startLive() }
        stopButton.setOnClickListener { stopLive() }
    }

    private fun startLive() {
        if (sourceBitmap == null) {
            status.text = "Select a source face first."
            return
        }
        val recognizer = modelRepository.model("arcface_112.onnx")
        val swapper = modelRepository.installedModels().firstOrNull { it.name in InswapperModelSpec.modelNames }
        if (recognizer == null || swapper == null) {
            status.text = "AI models missing · import ArcFace and INSwapper first"
            return
        }

        runCatching {
            stopProcessingOnly()
            detector = MlKitFaceDetector()
            embeddingEngine = OnnxInferenceEngine(recognizer.readBytes(), "ArcFace 112")
            swapperEngine = OnnxInferenceEngine(swapper.readBytes(), "INSwapper")
            swapProcessor = LiveSwapProcessor(
                detector = detector!!,
                embedder = ArcFaceEmbedder(embeddingEngine!!),
                swapper = InswapperOnnx(swapperEngine!!),
                compositor = FaceCompositor()
            )
            liveController.start()
            processedPreview.visibility = ImageView.VISIBLE
            processing.set(true)
            processingExecutor.execute { processFrames() }

            val endpoint = rtmpEndpoint.text.toString().trim()
            if (endpoint.isNotEmpty()) {
                rtmpOutput = RtmpLiveOutput(this) { message ->
                    runOnUiThread { if (!isFinishing && !isDestroyed) status.text = message }
                }.also { it.start(endpoint) }
                status.text = "Live AI active · RTMP connecting"
            } else {
                status.text = "Live AI active · processed preview running"
            }
        }.onFailure { error ->
            stopLive()
            status.text = "Live start failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun processFrames() {
        while (processing.get() && !isFinishing && !isDestroyed) {
            val frame = framePipeline.poll()
            if (frame == null) {
                Thread.sleep(5)
                continue
            }
            try {
                val output = swapProcessor?.process(frame.bitmap, sourceBitmap)
                val rendered = output?.frame
                if (rendered != null && output.status == LiveSwapStatus.Swapped) {
                    val streaming = rtmpOutput != null
                    if (streaming) {
                        val displayCopy = rendered.copy(Bitmap.Config.ARGB_8888, false)
                        rtmpOutput?.submit(rendered)
                        showProcessedPreview(displayCopy)
                    } else {
                        showProcessedPreview(rendered)
                    }
                } else if (output?.status == LiveSwapStatus.NoTargetFace) {
                    runOnUiThread { if (!isFinishing && !isDestroyed) status.text = "Live AI · face not detected" }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) status.text = "AI frame error: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    private fun showProcessedPreview(frame: Bitmap) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                if (!frame.isRecycled) frame.recycle()
                return@runOnUiThread
            }
            previewBitmap.replace(frame)
            previewBitmap.withBitmap { processedPreview.setImageBitmap(it) }
        }
    }

    private fun stopProcessingOnly() {
        processing.set(false)
        framePipeline.clear()
        rtmpOutput?.close()
        rtmpOutput = null
        runOnUiThread { previewBitmap.clear() }
        runCatching { embeddingEngine?.close() }
        runCatching { swapperEngine?.close() }
        embeddingEngine = null
        swapperEngine = null
        detector?.close()
        detector = null
        swapProcessor = null
    }

    private fun stopLive() {
        stopProcessingOnly()
        liveController.stop()
        processedPreview.visibility = ImageView.GONE
        processedPreview.setImageDrawable(null)
        status.text = "Live mode stopped"
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
        stopLive()
        stopCamera()
        appLock.lock()
        super.onPause()
    }

    override fun onDestroy() {
        stopLive()
        stopCamera()
        cameraExecutor.shutdownNow()
        processingExecutor.shutdownNow()
        previewBitmap.clear()
        sourceBitmap?.recycle()
        sourceBitmap = null
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed || appLock.isLocked()) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
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
                            onFrameDropped = {},
                            onFrameError = { error ->
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) status.text = "Frame error: ${error.message ?: "unsupported frame"}"
                                }
                            }
                        )
                    )
                }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            status.text = "Camera ready · select a source face"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
        framePipeline.clear()
    }
}
