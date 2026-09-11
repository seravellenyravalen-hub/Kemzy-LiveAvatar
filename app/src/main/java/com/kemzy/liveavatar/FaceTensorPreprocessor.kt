package com.kemzy.liveavatar

object FaceTensorPreprocessor {
    fun arcFace(rgbPixels: FloatArray, pixelCount: Int): FloatArray {
        require(rgbPixels.size == pixelCount) { "Expected one RGB intensity value per pixel" }
        val output = FloatArray(pixelCount * 3)
        for (i in 0 until pixelCount) {
            val normalized = (rgbPixels[i] - 127.5f) / 127.5f
            output[i] = normalized
            output[pixelCount + i] = normalized
            output[pixelCount * 2 + i] = normalized
        }
        return output
    }

    fun swapper(rgbPixels: FloatArray, pixelCount: Int): FloatArray {
        require(rgbPixels.size == pixelCount) { "Expected one RGB intensity value per pixel" }
        val output = FloatArray(pixelCount * 3)
        for (i in 0 until pixelCount) {
            val normalized = rgbPixels[i] / 255f
            output[i] = normalized
            output[pixelCount + i] = normalized
            output[pixelCount * 2 + i] = normalized
        }
        return output
    }
}
