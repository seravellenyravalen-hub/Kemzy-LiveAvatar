package com.kemzy.liveavatar

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

class LiaPortraitAnimator(
    modelFile: File,
    private val power: Float = 1.0f
) : Closeable {
    private val contract = LiaModelContract()
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions()
    )

    private var driverStartMotion: FloatArray? = null
    private var preparedSource: FloatArray? = null

    init {
        require(modelFile.isFile && modelFile.length() > 0L) {
            "LIA generator model is missing or empty: ${modelFile.absolutePath}"
        }
        require(session.inputNames.containsAll(
            setOf(contract.sourceInput, contract.driverInput, contract.startMotionInput, contract.powerInput)
        )) { "LIA generator inputs do not match the expected DeepFaceLive contract." }
        require(session.outputNames.containsAll(setOf(contract.motionOutput, contract.imageOutput))) {
            "LIA generator outputs do not match the expected DeepFaceLive contract."
        }
    }

    fun prepareSource(source: Bitmap) {
        preparedSource = LiaTensor.image(source)
        driverStartMotion = null
    }

    fun animate(driver: Bitmap): Bitmap {
        val source = preparedSource ?: error("LIA source portrait has not been prepared.")
        val driverTensor = LiaTensor.image(driver)
        val startMotion = driverStartMotion ?: extractMotion(driverTensor).also { driverStartMotion = it }

        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs[contract.sourceInput] = tensor(source, longArrayOf(1, 3, 256, 256))
            inputs[contract.driverInput] = tensor(driverTensor, longArrayOf(1, 3, 256, 256))
            inputs[contract.startMotionInput] = tensor(startMotion, longArrayOf(1, contract.startMotionSize.toLong()))
            inputs[contract.powerInput] = tensor(floatArrayOf(power), longArrayOf(1))
            session.run(inputs).use { results ->
                val value = results[contract.imageOutput].value as Array<*>? ?: error("LIA returned no image.")
                return LiaTensor.toBitmap(value)
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun extractMotion(driver: FloatArray): FloatArray {
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs[contract.sourceInput] = tensor(FloatArray(1 * 3 * 256 * 256), longArrayOf(1, 3, 256, 256))
            inputs[contract.driverInput] = tensor(driver, longArrayOf(1, 3, 256, 256))
            inputs[contract.startMotionInput] = tensor(FloatArray(contract.startMotionSize), longArrayOf(1, contract.startMotionSize.toLong()))
            inputs[contract.powerInput] = tensor(floatArrayOf(0f), longArrayOf(1))
            session.run(inputs).use { results ->
                val raw = results[contract.motionOutput].value
                return when (raw) {
                    is FloatArray -> raw.copyOf()
                    is Array<*> -> flattenFloatArray(raw)
                    else -> error("Unexpected LIA motion output type: ${raw?.javaClass}")
                }
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun tensor(values: FloatArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(environment, values, shape)

    private fun flattenFloatArray(value: Array<*>): FloatArray {
        val output = ArrayList<Float>()
        fun visit(node: Any?) {
            when (node) {
                is FloatArray -> node.forEach(output::add)
                is Array<*> -> node.forEach(::visit)
                is Number -> output.add(node.toFloat())
                null -> Unit
                else -> error("Unexpected nested LIA tensor value: ${node.javaClass}")
            }
        }
        visit(value)
        return output.toFloatArray()
    }

    override fun close() = session.close()
}

private object LiaTensor {
    fun image(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val pixels = IntArray(256 * 256)
        scaled.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        if (scaled !== bitmap) scaled.recycle()

        val out = FloatArray(1 * 3 * 256 * 256)
        val plane = 256 * 256
        for (y in 0 until 256) {
            for (x in 0 until 256) {
                val p = pixels[y * 256 + x]
                val r = ((p shr 16) and 0xff) / 255f * 2f - 1f
                val g = ((p shr 8) and 0xff) / 255f * 2f - 1f
                val b = (p and 0xff) / 255f * 2f - 1f
                val i = y * 256 + x
                out[i] = b
                out[plane + i] = g
                out[2 * plane + i] = r
            }
        }
        return out
    }

    fun toBitmap(value: Any?): Bitmap {
        val flat = flatten(value)
        require(flat.size >= 3 * 256 * 256) { "LIA output is smaller than 256x256x3." }
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(256 * 256)
        val plane = 256 * 256
        for (i in pixels.indices) {
            fun decode(v: Float): Int = (((v.coerceIn(-1f, 1f) + 1f) * 127.5f).toInt()).coerceIn(0, 255)
            val b = decode(flat[i])
            val g = decode(flat[plane + i])
            val r = decode(flat[2 * plane + i])
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, 256, 0, 0, 256, 256)
        return bitmap
    }

    private fun flatten(value: Any?): FloatArray {
        val output = ArrayList<Float>()
        fun visit(node: Any?) {
            when (node) {
                is FloatArray -> node.forEach(output::add)
                is Array<*> -> node.forEach(::visit)
                is Number -> output.add(node.toFloat())
                null -> Unit
                else -> error("Unexpected LIA image output type: ${node.javaClass}")
            }
        }
        visit(value)
        return output.toFloatArray()
    }
}
