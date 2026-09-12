package com.kemzy.liveavatar

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

object OnnxImageTensor {
    fun rgbNchw(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        val scaled = if (bitmap.width == size && bitmap.height == size) bitmap else Bitmap.createScaledBitmap(bitmap, size, size, true)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val values = FloatArray(size * size * 3)
        val plane = size * size
        for (y in 0 until size) for (x in 0 until size) {
            val p = pixels[y * size + x]; val i = y * size + x
            values[i] = (((p shr 16) and 0xff) - 127.5f) / 127.5f
            values[plane + i] = (((p shr 8) and 0xff) - 127.5f) / 127.5f
            values[plane * 2 + i] = ((p and 0xff) - 127.5f) / 127.5f
        }
        if (scaled !== bitmap) scaled.recycle()
        return values
    }
    fun tensor(env: ai.onnxruntime.OrtEnvironment, values: FloatArray, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)
}

class ArcFaceEmbedder(private val engine: OnnxInferenceEngine) : FaceEmbedder {
    override fun embedding(alignedSourceFace: Bitmap): FloatArray {
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val inputName = engine.inputNames().firstOrNull() ?: error("ArcFace model exposes no input.")
        val tensor = OnnxImageTensor.tensor(env, OnnxImageTensor.rgbNchw(alignedSourceFace, 112), 1L, 3L, 112L, 112L)
        tensor.use {
            val result = engine.run(mapOf(inputName to tensor))
            result.use {
                val output = result.values.firstOrNull() ?: error("ArcFace model returned no output.")
                return normalizeEmbedding(flattenFloatOutput(output))
            }
        }
    }
    private fun normalizeEmbedding(values: FloatArray): FloatArray {
        require(values.size == InswapperModelSpec.sourceEmbeddingSize) { "ArcFace output must contain 512 values, got ${values.size}." }
        var norm = 0.0; for (value in values) norm += value.toDouble() * value.toDouble()
        val scale = kotlin.math.sqrt(norm).toFloat().coerceAtLeast(1e-12f)
        return FloatArray(values.size) { values[it] / scale }
    }
}

class InswapperOnnx(private val engine: OnnxInferenceEngine) : FaceSwapper {
    override fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap {
        require(sourceEmbedding.size == InswapperModelSpec.sourceEmbeddingSize) { "INSwapper requires a 512-value source embedding." }
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment(); val names = engine.inputNames()
        val imageInput = names.firstOrNull { it.contains("img", true) || it.contains("target", true) || it.contains("input", true) }
            ?: names.firstOrNull() ?: error("INSwapper model exposes no inputs.")
        val latentInput = names.firstOrNull { it != imageInput } ?: error("INSwapper model must expose image and source-latent inputs.")
        val imageTensor = OnnxImageTensor.tensor(env, OnnxImageTensor.rgbNchw(alignedTargetFace, 128), 1L, 3L, 128L, 128L)
        val latentTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(sourceEmbedding), longArrayOf(1L, 512L))
        imageTensor.use { latentTensor.use {
            val result = engine.run(mapOf(imageInput to imageTensor, latentInput to latentTensor)); result.use {
                val output = result.values.firstOrNull() ?: error("INSwapper returned no output.")
                return bitmapFromOutput(flattenFloatOutput(output))
            }
        } }
    }
    private fun bitmapFromOutput(values: FloatArray): Bitmap {
        require(values.size == 3 * 128 * 128) { "INSwapper output must contain 3*128*128 values, got ${values.size}." }
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888); val pixels = IntArray(128 * 128); val plane = 128 * 128
        for (i in pixels.indices) { val r = toByte(values[i]); val g = toByte(values[plane + i]); val b = toByte(values[plane * 2 + i]); pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b }
        bitmap.setPixels(pixels, 0, 128, 0, 0, 128, 128); return bitmap
    }
    private fun toByte(value: Float): Int = (if (value in -1f..1f) (value + 1f) * 127.5f else value).coerceIn(0f, 255f).toInt()
}

private fun flattenFloatOutput(value: Any?): FloatArray = when (value) {
    is FloatArray -> value
    is Array<*> -> { val result = ArrayList<Float>(); fun walk(item: Any?) { when (item) { is FloatArray -> item.forEach(result::add); is Array<*> -> item.forEach(::walk); is Number -> result.add(item.toFloat()) } }; walk(value); result.toFloatArray() }
    is Number -> floatArrayOf(value.toFloat())
    else -> error("Unsupported ONNX output type: ${value?.javaClass?.name ?: "null"}")
}
