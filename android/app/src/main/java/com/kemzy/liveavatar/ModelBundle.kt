package com.kemzy.liveavatar

import java.io.File

/**
 * Files required by the Android runtime for a real Deep-Live-Cam style swap.
 * Model files stay outside the upstream repository tree and are imported into
 * the app's private storage.
 */
data class ModelBundle(
    val detector: File?,
    val recognizer: File?,
    val swapper: File?,
    val enhancer: File?
) {
    val isComplete: Boolean
        get() = detector != null && recognizer != null && swapper != null
}

class ModelBundleRepository(private val repository: ModelRepository) {
    fun inspect(): ModelBundle = ModelBundle(
        detector = repository.model("det_10g.onnx"),
        recognizer = repository.model("w600k_r50.onnx"),
        swapper = repository.model("inswapper_128_fp16.onnx")
            ?: repository.model("inswapper_128.onnx"),
        enhancer = repository.model("gfpgan-1024.onnx")
    )

    fun status(): String {
        val bundle = inspect()
        if (!bundle.isComplete) {
            val missing = buildList {
                if (bundle.detector == null) add("det_10g.onnx")
                if (bundle.recognizer == null) add("w600k_r50.onnx")
                if (bundle.swapper == null) add("inswapper_128.onnx or inswapper_128_fp16.onnx")
            }
            return "Missing required models: ${missing.joinToString()}. Import them before starting face processing."
        }
        return if (bundle.enhancer != null) {
            "Deep-Live-Cam model bundle ready; enhancement model installed."
        } else {
            "Deep-Live-Cam model bundle ready; enhancement is unavailable."
        }
    }
}
