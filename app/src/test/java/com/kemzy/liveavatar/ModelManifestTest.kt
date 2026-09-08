package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun required_models_include_embedder_and_swapper() {
        val required = FaceModelManifest.required
        assertEquals(2, required.size)
        assertTrue(required.any { it.id == "arcface-embedder" && it.fileName == "w600k_r50.onnx" })
        assertTrue(required.any { it.id == "face-swapper" && it.fileName == "inswapper_128.onnx" })
    }

    @Test
    fun emap_and_expression_restoration_are_optional() {
        assertTrue(FaceModelManifest.optional.any { it.id == "inswapper-emap" })
        assertTrue(FaceModelManifest.optional.any { it.id == "expression-restorer" })
        assertTrue(FaceModelManifest.required.none { it.id == "inswapper-emap" })
    }
}
