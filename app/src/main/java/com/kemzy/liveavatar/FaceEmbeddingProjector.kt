package com.kemzy.liveavatar

import kotlin.math.sqrt

object FaceEmbeddingProjector {
    fun projectAndNormalize(
        embedding: FloatArray,
        emap: FloatArray,
        outputDimension: Int
    ): FloatArray {
        require(embedding.isNotEmpty()) { "Embedding must not be empty" }
        require(outputDimension > 0) { "Output dimension must be positive" }
        require(emap.size == embedding.size * outputDimension) {
            "EMAP must contain embeddingSize * outputDimension values"
        }

        val projected = FloatArray(outputDimension)
        for (out in 0 until outputDimension) {
            var sum = 0f
            for (input in embedding.indices) {
                sum += embedding[input] * emap[input * outputDimension + out]
            }
            projected[out] = sum
        }

        var squaredNorm = 0f
        for (value in projected) squaredNorm += value * value
        val norm = sqrt(squaredNorm)
        require(norm > 1e-12f) { "Projected embedding has zero norm" }
        for (i in projected.indices) projected[i] /= norm
        return projected
    }
}
