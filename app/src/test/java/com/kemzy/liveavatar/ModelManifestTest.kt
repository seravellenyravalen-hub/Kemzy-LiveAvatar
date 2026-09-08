package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun required_models_include_detector_embedder_and_swapper() {
        val required = FaceModelManifest.required
        assertEquals(3, required.size)
        assertTrue(required.any { it.id == "face-detector" })
        assertTrue(required.any { it.id == "arcface-embedder" })
        assertTrue(required.any { it.id == "face-swapper" })
    }

    @Test
    fun optional_expression_restorer_is_not_required_for_engine_readiness() {
        assertTrue(FaceModelManifest.optional.any { it.id == "expression-restorer" })
        assertTrue(FaceModelManifest.required.none { it.id == "expression-restorer" })
    }
}
