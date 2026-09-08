package com.kemzy.liveavatar

import android.content.Context
import android.os.Build
import ai.onnxruntime.OrtSession

class OnDeviceFaceSwapEngine(
    private val context: Context? = null,
    private val missingModelsOverride: List<String>? = null
) : FaceSwapEngine {
    constructor(missingModels: List<String>) : this(
        context = null,
        missingModelsOverride = missingModels
    )

    override var avatarUri: String? = null
        private set

    override var state: LiveFaceEngineState = LiveFaceEngineState.Idle
        private set

    private var embedderSession: OrtSession? = null
    private var swapperSession: OrtSession? = null

    override fun setAvatar(uri: String) {
        avatarUri = uri
        state = LiveFaceEngineState.Preparing
    }

    override fun prepareAvatar(): LiveFaceEngineState {
        if (avatarUri.isNullOrBlank()) {
            state = LiveFaceEngineState.Fallback("No avatar selected")
            return state
        }

        val missing = missingModelsOverride ?: run {
            val appContext = context
                ?: return LiveFaceEngineState.Fallback("Android context is required")
            val store = ModelStore(appContext)
            buildList {
                addAll(store.missingRequiredModels().map { it.id })
                if (!store.isPresent(FaceModelManifest.optional.first { it.id == "inswapper-emap" })) {
                    add("inswapper-emap")
                }
            }
        }

        if (missing.isNotEmpty()) {
            state = LiveFaceEngineState.Fallback("Missing models: ${missing.joinToString(", ")}")
            return state
        }

        val appContext = context
        if (appContext == null) {
            state = LiveFaceEngineState.Ready
            return state
        }

        return try {
            val store = ModelStore(appContext)
            val backend = InferenceBackendSelector.select(
                apiLevel = Build.VERSION.SDK_INT,
                nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
            )
            val factory = OnnxSessionFactory()
            embedderSession = factory.create(
                store.fileFor(FaceModelManifest.required.first { it.id == "arcface-embedder" }).absolutePath,
                backend
            )
            swapperSession = factory.create(
                store.fileFor(FaceModelManifest.required.first { it.id == "face-swapper" }).absolutePath,
                backend
            )
            state = LiveFaceEngineState.Ready
            state
        } catch (error: Exception) {
            embedderSession?.close()
            swapperSession?.close()
            embedderSession = null
            swapperSession = null
            state = LiveFaceEngineState.Fallback(
                "AI model runtime failed to initialize: ${error.message ?: "unknown error"}"
            )
            state
        }
    }

    override fun processFrame(tracking: FaceTrackingResult): FaceSwapFrame =
        FaceSwapFrame.fallback(
            when (state) {
                LiveFaceEngineState.Ready -> "AI runtime loaded; frame swap stage is not connected yet"
                is LiveFaceEngineState.Fallback -> state.reason
                LiveFaceEngineState.Preparing -> "AI models are preparing"
                LiveFaceEngineState.Idle -> "AI engine is idle"
            }
        )

    override fun clearAvatar() {
        avatarUri = null
        state = LiveFaceEngineState.Idle
    }

    override fun close() {
        embedderSession?.close()
        swapperSession?.close()
        embedderSession = null
        swapperSession = null
    }
}
