package com.kemzy.liveavatar

import java.io.File

/** Explicit model roles required before the real live swap engine can start. */
data class LiveModelBundle(
    val detector: File?,
    val recognizer: File?,
    val swapper: File?
) {
    val isComplete: Boolean
        get() = detector.isUsable() && recognizer.isUsable() && swapper.isUsable()

    fun missingRoles(): List<String> = buildList {
        if (!detector.isUsable()) add("face detector")
        if (!recognizer.isUsable()) add("face recognizer / 512-D embedder")
        if (!swapper.isUsable()) add("INSwapper")
    }

    private fun File?.isUsable(): Boolean = this != null && isFile && length() > 0L
}

class LiveModelBundleRepository(
    private val modelRepository: ModelRepository
) {
    fun inspect(): LiveModelBundle = LiveModelBundle(
        detector = modelRepository.model("face_detector.onnx"),
        recognizer = modelRepository.model("arcface_112.onnx"),
        swapper = modelRepository.installedModels().firstOrNull {
            it.name in InswapperModelSpec.modelNames
        }
    )
}
