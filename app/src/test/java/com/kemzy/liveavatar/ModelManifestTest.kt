package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun required_models_include_complete_live_swap_pipeline() {
        val required = FaceModelManifest.required
        assertEquals(3, required.size)
        assertTrue(required.any { it.id == "arcface-embedder" && it.fileName == "w600k_r50.onnx" })
        assertTrue(required.any { it.id == "face-swapper" && it.fileName == "inswapper_128.onnx" })
        assertTrue(required.any { it.id == "inswapper-emap" && it.fileName == "emap.bin" })
    }

    @Test
    fun expression_restoration_remains_optional() {
        assertTrue(FaceModelManifest.optional.any { it.id == "expression-restorer" })
        assertTrue(FaceModelManifest.required.none { it.id == "expression-restorer" })
    }
}
