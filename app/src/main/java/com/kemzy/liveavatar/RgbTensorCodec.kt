package com.kemzy.liveavatar

/** Converts packed RGB pixels (R,G,B per pixel) into ONNX NCHW float tensors. */
object RgbTensorCodec {
    fun arcFace(rgb: FloatArray, width: Int, height: Int): FloatArray =
        normalize(rgb, width, height) { (it - 127.5f) / 127.5f }

    fun swapper(rgb: FloatArray, width: Int, height: Int): FloatArray =
        normalize(rgb, width, height) { it / 255f }

    private fun normalize(
        rgb: FloatArray,
        width: Int,
        height: Int,
        transform: (Float) -> Float
    ): FloatArray {
        require(width > 0 && height > 0)
        require(rgb.size == width * height * 3) {
            "Expected RGB triplets for ${width}x${height} image"
        }

        val pixels = width * height
        val output = FloatArray(pixels * 3)
        for (pixel in 0 until pixels) {
            val base = pixel * 3
            output[pixel] = transform(rgb[base])
            output[pixels + pixel] = transform(rgb[base + 1])
            output[pixels * 2 + pixel] = transform(rgb[base + 2])
        }
        return output
    }
}
