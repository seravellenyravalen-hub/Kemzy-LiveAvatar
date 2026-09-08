package com.kemzy.liveavatar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FaceEmbeddingProjectorTest {
    @Test
    fun projectsEmbeddingThroughEMapAndNormalizes() {
        val embedding = floatArrayOf(3f, 4f, 0f)
        val emap = floatArrayOf(
            1f, 0f,
            0f, 1f,
            0f, 0f
        )

        val projected = FaceEmbeddingProjector.projectAndNormalize(
            embedding = embedding,
            emap = emap,
            outputDimension = 2
        )

        assertArrayEquals(floatArrayOf(0.6f, 0.8f), projected, 0.0001f)
    }

    @Test
    fun rejectsInvalidEMapSize() {
        assertThrows(IllegalArgumentException::class.java) {
            FaceEmbeddingProjector.projectAndNormalize(
                embedding = floatArrayOf(1f, 2f, 3f),
                emap = floatArrayOf(1f, 2f),
                outputDimension = 2
            )
        }
    }
}
