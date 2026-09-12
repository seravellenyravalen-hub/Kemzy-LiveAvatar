package com.kemzy.liveavatar

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object OnnxImageTensor {
    fun rgbNchw(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        val scaled = if (bitmap.width == size && bitmap.height == size) bitmap else Bitmap.createScaledBitmap(bitmap, size, size, true)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val values = FloatArray(size * size * 3)
        val plane = size * size
        for (y in 0 until size) for (x in 0 until size) {
            val p = pixels[y * size + x]
            val r = ((p shr 16) and 0xff).toFloat()
            val g = ((p shr 8) and 0xff).toFloat()
            val b = (p and 0xff).toFloat()
            val i = y * size + x
            values[i] = (r - 127.5f) / 127.5f
            values[plane + i] = (g - 127.5f) / 127.5f
            values[plane * 2 + i] = (b - 127.5f) / 127.5f
        }
        if (scaled !== bitmap) scaled.recycle()
        return values
    }

    fun inswapperRgbNchw(bitmap: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        val scaled = if (bitmap.width == size && bitmap.height == size) bitmap else Bitmap.createScaledBitmap(bitmap, size, size, true)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val values = FloatArray(size * size * 3)
        val plane = size * size
        for (y in 0 until size) for (x in 0 until size) {
            val p = pixels[y * size + x]
            val i = y * size + x
            values[i] = ((p shr 16) and 0xff) / 255f
            values[plane + i] = ((p shr 8) and 0xff) / 255f
            values[plane * 2 + i] = (p and 0xff) / 255f
        }
        if (scaled !== bitmap) scaled.recycle()
        return values
    }

    fun tensor(env: ai.onnxruntime.OrtEnvironment, values: FloatArray, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)
}

class ArcFaceEmbedder(private val engine: InferenceEngine) : FaceEmbedder {
    override fun embedding(alignedSourceFace: Bitmap): FloatArray {
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val inputName = engine.inputNames().firstOrNull() ?: error("ArcFace model exposes no input.")
        val tensor = OnnxImageTensor.tensor(env, OnnxImageTensor.rgbNchw(alignedSourceFace, 112), 1L, 3L, 112L, 112L)
        tensor.use {
            val output = engine.run(mapOf(inputName to tensor)).values.firstOrNull() ?: error("ArcFace model returned no output.")
            return normalizeEmbedding(flattenFloatOutput(output))
        }
    }

    private fun normalizeEmbedding(values: FloatArray): FloatArray {
        require(values.size == InswapperModelSpec.sourceEmbeddingSize) { "ArcFace output must contain 512 values, got ${values.size}." }
        var norm = 0.0
        for (value in values) norm += value.toDouble() * value.toDouble()
        val scale = kotlin.math.sqrt(norm).toFloat().coerceAtLeast(1e-12f)
        return FloatArray(values.size) { values[it] / scale }
    }
}

/** INSwapper requires the model's 512x512 EMAP projection before inference. */
class InswapperOnnx(
    private val engine: InferenceEngine,
    emapFile: File
) : FaceSwapper {
    private val emap = loadEmap(emapFile)

    override fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap {
        require(sourceEmbedding.size == InswapperModelSpec.sourceEmbeddingSize) { "INSwapper requires a 512-value source embedding." }
        val latent = projectEmbedding(sourceEmbedding, emap)
        val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val imageInput = engine.inputNames().firstOrNull { name ->
            name.equals("target", true) || name.contains("img", true) || name.contains("target", true)
        } ?: engine.inputNames().firstOrNull() ?: error("INSwapper model exposes no inputs.")
        val latentInput = engine.inputNames().firstOrNull { it != imageInput } ?: error("INSwapper model must expose image and source-latent inputs.")
        val imageTensor = OnnxImageTensor.tensor(env, OnnxImageTensor.inswapperRgbNchw(alignedTargetFace, 128), 1L, 3L, 128L, 128L)
        val latentTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1L, 512L))
        imageTensor.use {
            latentTensor.use {
                val output = engine.run(mapOf(imageInput to imageTensor, latentInput to latentTensor)).values.firstOrNull()
                    ?: error("INSwapper returned no output.")
                return bitmapFromOutput(flattenFloatOutput(output))
            }
        }
    }

    private fun bitmapFromOutput(values: FloatArray): Bitmap {
        require(values.size == 3 * 128 * 128) { "INSwapper output must contain 3*128*128 values, got ${values.size}." }
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(128 * 128)
        val plane = 128 * 128
        for (i in pixels.indices) {
            val r = (values[i].coerceIn(0f, 1f) * 255f).toInt()
            val g = (values[plane + i].coerceIn(0f, 1f) * 255f).toInt()
            val b = (values[plane * 2 + i].coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, 128, 0, 0, 128, 128)
        return bitmap
    }

    private fun loadEmap(file: File): FloatArray {
        require(file.isFile && file.length() == 512L * 512L * 4L) { "INSwapper EMAP is missing or invalid. Import inswapper_emap.bin (1,048,576 bytes)." }
        val bytes = file.readBytes()
        val floats = FloatArray(512 * 512)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }

    private fun projectEmbedding(embedding: FloatArray, matrix: FloatArray): FloatArray {
        val latent = FloatArray(512)
        for (column in 0 until 512) {
            var sum = 0.0
            for (row in 0 until 512) sum += embedding[row].toDouble() * matrix[row * 512 + column].toDouble()
            latent[column] = sum.toFloat()
        }
        var norm = 0.0
        for (value in latent) norm += value.toDouble() * value.toDouble()
        val scale = kotlin.math.sqrt(norm).toFloat().coerceAtLeast(1e-12f)
        return FloatArray(512) { latent[it] / scale }
    }
}

@Suppress("UNCHECKED_CAST")
private fun flattenFloatOutput(value: Any?): FloatArray {
    when (value) {
        is FloatArray -> return value
        is Array<*> -> {
            val result = ArrayList<Float>()
            fun walk(item: Any?) {
                when (item) {
                    is FloatArray -> item.forEach(result::add)
                    is Array<*> -> item.forEach(::walk)
                    is Number -> result.add(item.toFloat())
                }
            }
            walk(value)
            return result.toFloatArray()
        }
        is Number -> return floatArrayOf(value.toFloat())
    }
    error("Unsupported ONNX output type: ${value?.javaClass?.name ?: "null"}")
}
