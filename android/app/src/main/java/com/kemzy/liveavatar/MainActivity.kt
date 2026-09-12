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
    private var portraitAnimator: LiaPortraitAnimator? = null
    private var rtmpOutput: RtmpLiveOutput? = null
    private var pendingModelName: String? = null
    private val processing = AtomicBoolean(false)

    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sourceBitmap?.recycle()
            sourceBitmap = sourceLoader.load(uri)
            sourceFaces.select(uri.toString())
            liveState.selectSource(uri.toString())
            sourceText.text = "Source portrait: selected"
            status.text = "Source portrait ready · tap LIVE"
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

    private val liaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            modelRepository.importLiaGenerator(contentResolver, uri)
            status.text = "LIA generator imported · ready for Live"
        }.onFailure { error -> status.text = "LIA import failed: ${error.message ?: "invalid generator"}" }
    }

    private val emapPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            modelRepository.importEmap(contentResolver, uri)
            status.text = "INSwapper EMAP imported"
        }.onFailure { error -> status.text = "EMAP import failed: ${error.message ?: "invalid EMAP"}" }
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
        findViewById<Button>(R.id.importLiaButton).setOnClickListener {
            liaPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }
        findViewById<Button>(R.id.importArcFaceButton).setOnClickListener {
            pendingModelName = InswapperModelSpec.recognizerModelName
            modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }
        findViewById<Button>(R.id.importInswapperButton).setOnClickListener {
            pendingModelName = "inswapper_128.onnx"
            modelPicker.launch(arrayOf("application/octet-stream", "application/onnx", "*/*"))
        }
        findViewById<Button>(R.id.importEmapButton).setOnClickListener {
            emapPicker.launch(arrayOf("application/octet-stream", "application/octet-stream+binary", "*/*"))
        }
        liveButton.setOnClickListener { startLive() }
        stopButton.setOnClickListener { stopLive() }
    }

    private fun startLive() {
        val source = sourceBitmap
        if (source == null) {
            status.text = "Select a source portrait first."
            return
        }
        val liaModel = modelRepository.liaModel()
        if (liaModel == null) {
            status.text = "LIA generator missing · import generator.onnx first"
            return
        }

        runCatching {
            stopProcessingOnly()
            portraitAnimator = LiaPortraitAnimator(liaModel).also { it.prepareSource(source) }
            liveController.start()
            processedPreview.visibility = ImageView.VISIBLE
            processing.set(true)
            processingExecutor.execute { processFrames() }

            val endpoint = rtmpEndpoint.text.toString().trim()
            if (endpoint.isNotEmpty()) {
                rtmpOutput = RtmpLiveOutput(this) { message ->
                    runOnUiThread { if (!isFinishing && !isDestroyed) status.text = message }
                }.also { it.start(endpoint) }
                status.text = "Photo Live active · RTMP connecting"
            } else {
                status.text = "Photo Live active · continuous portrait animation"
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
                val animated = portraitAnimator?.animate(frame.bitmap)
                if (animated != null) {
                    val streaming = rtmpOutput != null
                    if (streaming) {
                        val displayCopy = animated.copy(Bitmap.Config.ARGB_8888, false)
                        rtmpOutput?.submit(animated)
                        showProcessedPreview(displayCopy)
                    } else {
                        showProcessedPreview(animated)
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) status.text = "Photo Live frame error: ${error.message ?: error.javaClass.simpleName}"
                }
            } finally {
                if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
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
        runCatching { portraitAnimator?.close() }
        portraitAnimator = null
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        stopLive()
        stopCamera()
        super.onPause()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(cameraExecutor, LiveFrameAnalyzer(framePipeline))
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { ProcessCameraProvider.getInstance(this).get().unbindAll() }
    }

    override fun onDestroy() {
        stopLive()
        sourceBitmap?.recycle()
        cameraExecutor.shutdownNow()
        processingExecutor.shutdownNow()
        super.onDestroy()
    }
}
