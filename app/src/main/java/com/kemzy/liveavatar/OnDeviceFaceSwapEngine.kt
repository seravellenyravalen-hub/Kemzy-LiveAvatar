package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import ai.onnxruntime.OrtSession

class OnDeviceFaceSwapEngine(
    private val context: Context? = null,
    private val missingModelsOverride: List<String>? = null
) : FaceSwapEngine {
    constructor(missingModels: List<String>) : this(context = null, missingModelsOverride = missingModels)

    override var avatarUri: String? = null
        private set
    override var state: LiveFaceEngineState = LiveFaceEngineState.Idle
        private set

    private var embedderSession: OrtSession? = null
    private var swapperSession: OrtSession? = null
    private var processor: NeuralFaceSwapProcessor? = null

    override fun setAvatar(uri: String) {
        check(state !is LiveFaceEngineState.Ready) { "Reference cannot change while the engine is live" }
        avatarUri = uri
        processor?.clear()
        state = LiveFaceEngineState.Preparing
    }

    override fun prepareAvatar(): LiveFaceEngineState {
        val reference = avatarUri
        if (reference.isNullOrBlank()) {
            state = LiveFaceEngineState.Fallback("No avatar selected")
            return state
        }

        if (!missingModelsOverride.isNullOrEmpty()) {
            state = LiveFaceEngineState.Fallback("Required runtime models are unavailable")
            return state
        }

        val appContext = context ?: run {
            state = LiveFaceEngineState.Fallback("Android context is required")
            return state
        }

        return try {
            val store = ModelStore(appContext)
            ensureRequiredModels(appContext, store)
            val missing = store.missingRequiredModels().map { it.id }
            if (missing.isNotEmpty()) {
                state = LiveFaceEngineState.Fallback("Required runtime models are unavailable")
                return state
            }

            val factory = OnnxSessionFactory()
            val arcFace = FaceModelManifest.required.first { it.id == "arcface-embedder" }
            val swapper = FaceModelManifest.required.first { it.id == "face-swapper" }
            val emap = FaceModelManifest.required.first { it.id == "inswapper-emap" }
            val activeProcessor = NeuralFaceSwapProcessor(appContext)
            processor = activeProcessor

            // Phase 1: load only ArcFace. The swapper is deliberately NOT loaded yet.
            // On low-memory Android devices, keeping both heavyweight sessions alive
            // crosses the app heap limit and produces the observed allocation/OOM error.
            var embedderLoaded = false
            var lastEmbedderError: Throwable? = null
            for (backend in inferenceBackends()) {
                closeEmbedderSession()
                try {
                    embedderSession = factory.create(store.fileFor(arcFace).absolutePath, backend)
                    activeProcessor.attachEmbedderSession(embedderSession!!)
                    embedderLoaded = true
                    lastEmbedderError = null
                    break
                } catch (error: Throwable) {
                    lastEmbedderError = error
                    closeEmbedderSession()
                }
            }

            if (!embedderLoaded) {
                val detail = lastEmbedderError?.message ?: "no compatible execution provider"
                state = LiveFaceEngineState.Fallback("AI runtime could not load ArcFace: $detail")
                return state
            }

            val preparationError = activeProcessor.prepareAvatar(reference, store.fileFor(emap))
            closeEmbedderSession()
            if (preparationError != null) {
                activeProcessor.clear()
                state = LiveFaceEngineState.Fallback(preparationError)
                return state
            }

            // Phase 2: ArcFace is no longer needed. Only now load INSwapper.
            var swapperLoaded = false
            var lastSwapperError: Throwable? = null
            for (backend in inferenceBackends()) {
                closeSwapperSession()
                try {
                    swapperSession = factory.create(store.fileFor(swapper).absolutePath, backend)
                    activeProcessor.attachSwapperSession(swapperSession!!)
                    swapperLoaded = true
                    lastSwapperError = null
                    break
                } catch (error: Throwable) {
                    lastSwapperError = error
                    closeSwapperSession()
                }
            }

            if (!swapperLoaded) {
                activeProcessor.clear()
                val detail = lastSwapperError?.message ?: "no compatible execution provider"
                state = LiveFaceEngineState.Fallback("AI runtime could not load face swapper: $detail")
                return state
            }

            state = LiveFaceEngineState.Ready
            state
        } catch (error: Exception) {
            closeSessions()
            state = LiveFaceEngineState.Fallback("AI runtime failed to initialize: ${error.message ?: "unknown error"}")
            state
        }
    }

    private fun inferenceBackends(): List<InferenceBackend> =
        InferenceBackendSelector.candidates(
            apiLevel = Build.VERSION.SDK_INT,
            nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        )

    private fun ensureRequiredModels(appContext: Context, store: ModelStore) {
        if (store.missingRequiredModels().isEmpty()) return
        val downloader = ModelDownloader(appContext)
        var lastError: Exception? = null
        repeat(3) {
            try {
                downloader.downloadRequired()
                lastError = null
                return
            } catch (error: Exception) {
                lastError = error
            }
        }
        lastError?.let { throw it }
    }

    private fun closeEmbedderSession() {
        processor?.detachEmbedderSession()
        embedderSession?.close()
        embedderSession = null
    }

    private fun closeSwapperSession() {
        processor?.detachSwapperSession()
        swapperSession?.close()
        swapperSession = null
    }

    private fun closeSessions() {
        processor?.clear()
        closeEmbedderSession()
        closeSwapperSession()
        processor = null
    }

    override fun processFrame(tracking: FaceTrackingResult): FaceSwapFrame =
        FaceSwapFrame.fallback("Live neural frame required")

    override fun processFrame(frame: Bitmap, tracking: FaceTrackingResult): FaceSwapFrame {
        if (state !is LiveFaceEngineState.Ready) return processFrame(tracking)
        return try {
            val output = processor?.process(frame, tracking)
            if (output != null) FaceSwapFrame.neural(output)
            else FaceSwapFrame.fallback("Neural frame inference produced no output")
        } catch (error: Exception) {
            FaceSwapFrame.fallback("Neural frame inference failed: ${error.message ?: "unknown error"}")
        }
    }

    override fun clearAvatar() {
        avatarUri = null
        processor?.clear()
        state = LiveFaceEngineState.Idle
    }

    override fun close() = closeSessions()
}
