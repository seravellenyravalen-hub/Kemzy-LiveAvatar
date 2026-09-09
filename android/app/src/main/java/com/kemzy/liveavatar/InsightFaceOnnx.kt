package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Minimal Android-side adapters for the same model contracts used by the
 * Deep-Live-Cam/InsightFace family. The actual ONNX weights are user supplied.
 */
class ArcFaceOnnxRecognizer(
    modelBytes: ByteArray,
    private val inputName: String? = null,
    private val outputName: String? = null
) : FaceRecognizer, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelBytes, OrtSession.SessionOptions())
    private val resolvedInput = inputName ?: session.inputNames.first()
    private val resolvedOutput = outputName ?: session.outputNames.first()

    override fun embedding(face: Bitmap): FloatArray {
        val aligned = Bitmap.createScaledBitmap(face, 112, 112, true)
        val values = FloatArray(1 * 3 * 112 * 112)
        var offset = 0
        for (channel in 0..2) {
            for (y in 0 until 112) {
                for (x in 0 until 112) {
                    val pixel = aligned.getPixel(x, y)
                    val raw = when (channel) {
                        0 -> android.graphics.Color.red(pixel)
                        1 -> android.graphics.Color.green(pixel)
                        else -> android.graphics.Color.blue(pixel)
                    }
                    values[offset++] = (raw - 127.5f) / 127.5f
                }
            }
        }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 3, 112, 112)).use { tensor ->
            session.run(mapOf(resolvedInput to tensor)).use { result ->
                val raw = result[resolvedOutput]?.value
                    ?: error("ArcFace returned no embedding")
                val vector = when (raw) {
                    is Array<*> -> flattenFloats(raw)
                    is FloatArray -> raw
                    else -> error("Unsupported ArcFace output type: ${raw::class.java.name}")
                }
                require(vector.size >= 512) { "ArcFace embedding must contain at least 512 values" }
                return l2Normalize(vector.copyOf(512))
            }
        }
    }

    override fun close() = session.close()
}

class InSwapperOnnx(
    modelBytes: ByteArray,
    private val imageInputName: String? = null,
    private val embeddingInputName: String? = null,
    private val outputName: String? = null
) : FaceSwapper, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelBytes, OrtSession.SessionOptions())
    private val inputs = session.inputNames.toList()
    private val outputs = session.outputNames.toList()

    override fun swap(alignedTarget: Bitmap, sourceEmbedding: FloatArray): Bitmap {
        require(sourceEmbedding.size == 512) { "INSwapper requires a 512-D source embedding" }
        val imageName = imageInputName ?: inputs.firstOrNull { name ->
            val shape = session.inputInfo[name]?.info
            shape?.toString()?.contains("128") == true
        } ?: inputs.first()
        val embeddingName = embeddingInputName ?: inputs.firstOrNull { it != imageName }
            ?: error("INSwapper model must expose image and embedding inputs")
        val outName = outputName ?: outputs.first()

        val pixels = FloatArray(1 * 3 * 128 * 128)
        var offset = 0
        val resized = Bitmap.createScaledBitmap(alignedTarget, 128, 128, true)
        for (channel in 0..2) {
            for (y in 0 until 128) {
                for (x in 0 until 128) {
                    val p = resized.getPixel(x, y)
                    val value = when (channel) {
                        0 -> android.graphics.Color.red(p)
                        1 -> android.graphics.Color.green(p)
                        else -> android.graphics.Color.blue(p)
                    }
                    pixels[offset++] = value / 127.5f - 1.0f
                }
            }
        }

        OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixels), longArrayOf(1, 3, 128, 128)).use { imageTensor ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(sourceEmbedding), longArrayOf(1, 512)).use { embeddingTensor ->
                session.run(mapOf(imageName to imageTensor, embeddingName to embeddingTensor)).use { result ->
                    val raw = result[outName]?.value ?: error("INSwapper returned no image")
                    val output = when (raw) {
                        is Array<*> -> flattenFloats(raw)
                        is FloatArray -> raw
                        else -> error("Unsupported INSwapper output type: ${raw::class.java.name}")
                    }
                    require(output.size >= 3 * 128 * 128) { "INSwapper output is smaller than 128x128 RGB" }
                    return bitmapFromNchw(output)
                }
            }
        }
    }

    override fun close() = session.close()
}

private fun flattenFloats(value: Any): FloatArray {
    return when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flattenFloats(it ?: emptyArray<Any>()).asList() }.toFloatArray()
        else -> error("Expected float tensor, got ${value::class.java.name}")
    }
}

private fun l2Normalize(values: FloatArray): FloatArray {
    val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() }).toFloat().coerceAtLeast(1e-8f)
    return FloatArray(values.size) { values[it] / norm }
}

private fun bitmapFromNchw(values: FloatArray): Bitmap {
    val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
    var index = 0
    for (y in 0 until 128) {
        for (x in 0 until 128) {
            val r = ((values[index].coerceIn(-1f, 1f) + 1f) * 127.5f).toInt(); index++
            val g = ((values[128 * 128 + y * 128 + x].coerceIn(-1f, 1f) + 1f) * 127.5f).toInt()
            val b = ((values[2 * 128 * 128 + y * 128 + x].coerceIn(-1f, 1f) + 1f) * 127.5f).toInt()
            bitmap.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
        }
    }
    return bitmap
}
