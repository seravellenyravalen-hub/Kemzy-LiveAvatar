package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun required_models_are_exactly_the_live_swap_pipeline() {
        val required = FaceModelManifest.required
        assertEquals(2, required.size)
        assertTrue(required.any { it.id == "arcface-embedder" && it.fileName == "w600k_r50.onnx" })
        assertTrue(required.any { it.id == "face-swapper" && it.fileName == "inswapper_128_fp16.onnx" })
    }

    @Test
    fun no_unused_expression_or_emap_models_are_required() {
        assertTrue(FaceModelManifest.optional.isEmpty())
        assertTrue(FaceModelManifest.required.none { it.id == "expression-restorer" })
        assertTrue(FaceModelManifest.required.none { it.id == "inswapper-emap" })
    }
}
