package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InswapperContractTest {
    @Test
    fun inswapper128UsesDesktopCompatibleInputShape() {
        val spec = InswapperModelSpec

        assertEquals(128, spec.faceWidth)
        assertEquals(128, spec.faceHeight)
        assertEquals(512, spec.sourceEmbeddingSize)
        assertTrue(spec.modelNames.contains("inswapper_128.onnx"))
        assertTrue(spec.modelNames.contains("inswapper_128_fp16.onnx"))
    }
}
