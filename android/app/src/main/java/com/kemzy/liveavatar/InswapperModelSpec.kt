package com.kemzy.liveavatar

/**
 * Compatibility contract for the desktop Deep-Live-Cam INSwapper 128 family.
 * The binary model remains user-supplied because it is not part of source code.
 */
object InswapperModelSpec {
    const val faceWidth = 128
    const val faceHeight = 128
    const val sourceEmbeddingSize = 512

    val modelNames: Set<String> = setOf(
        "inswapper_128.onnx",
        "inswapper_128_fp16.onnx"
    )
}
