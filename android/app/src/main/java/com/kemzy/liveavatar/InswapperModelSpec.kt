package com.kemzy.liveavatar

/**
 * Compatibility contract for the desktop Deep-Live-Cam INSwapper 128 family.
 * The binary models remain user-supplied because they are not part of source code.
 */
object InswapperModelSpec {
    const val faceWidth = 128
    const val faceHeight = 128
    const val sourceEmbeddingSize = 512
    const val recognizerModelName = "w600k_r50.onnx"

    val modelNames: Set<String> = setOf(
        "inswapper_128.onnx",
        "inswapper_128_fp16.onnx"
    )
}
