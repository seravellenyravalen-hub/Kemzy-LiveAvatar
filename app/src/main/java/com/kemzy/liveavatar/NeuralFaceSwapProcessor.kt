package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Local ArcFace -> EMAP -> INSwapper pipeline.
 * The selected reference is prepared once; every camera frame is transformed from
 * the live tracked face. The network is never consulted during frame processing.
 */
class NeuralFaceSwapProcessor(
    private val context: Context,
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
) {
    private var embedder: OrtSession? = null
    private var swapper: OrtSession? = null
    private var sourceLatent: FloatArray? = null

    fun attachSessions(embedderSession: OrtSession, swapperSession: OrtSession) {
        embedder = embedderSession
        swapper = swapperSession
    }

    fun prepareAvatar(uri: String, emapFile: java.io.File): String? {
        val parsed = Uri.parse(uri)
        val source = context.contentResolver.openInputStream(parsed).use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: return "Could not decode selected avatar"

        if (!emapFile.isFile || emapFile.length() <= 0L) {
            source.recycle()
            return "Missing inswapper EMAP data"
        }

        return try {
            val sourceFace = detectAndCropSourceFace(source)
            val embedding = runArcFace(sourceFace)
            val emap = loadEMap(emapFile, embedding.size, 512)
            sourceLatent = FaceEmbeddingProjector.projectAndNormalize(embedding, emap, 512)
            sourceFace.recycle()
            null
        } catch (error: Exception) {
            sourceLatent = null
            "Avatar AI preparation failed: ${error.message ?: "unknown error"}"
        } finally {
            source.recycle()
        }
    }

    fun process(frame: Bitmap, tracking: FaceTrackingResult): Bitmap? {
        val latent = sourceLatent ?: return null
        val swapperSession = swapper ?: return null
        if (tracking.faceCount <= 0) return null

        val crop = FaceSwapGeometry.targetCrop(tracking, frame.width, frame.height, margin = 0.25f)
        val target = Bitmap.createBitmap(frame, crop.left, crop.top, crop.width, crop.height)
        val target128 = Bitmap.createScaledBitmap(target, 128, 128, true)
        return try {
            val swapped = runSwapper(swapperSession, target128, latent)
            val resized = Bitmap.createScaledBitmap(swapped, crop.width, crop.height, true)
            val output = frame.copy(Bitmap.Config.ARGB_8888, true)
            blendFace(output, resized, crop.left, crop.top)
            swapped.recycle()
            resized.recycle()
            output
        } finally {
            target.recycle()
            target128.recycle()
        }
    }

    fun clear() {
        sourceLatent = null
    }

    private fun detectAndCropSourceFace(source: Bitmap): Bitmap {
        val detector = FaceDetection.getClient()
        return try {
            val image = InputImage.fromBitmap(source, 0)
            val faces = Tasks.await(detector.process(image))
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                ?: return squareCrop(source)

            val bounds = face.boundingBox
            val faceWidth = bounds.width().coerceAtLeast(1)
            val faceHeight = bounds.height().coerceAtLeast(1)
            val side = (max(faceWidth, faceHeight) * 1.55f)
                .toInt()
                .coerceIn(1, minOf(source.width, source.height))
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val left = (centerX - side / 2f).toInt().coerceIn(0, source.width - side)
            val top = (centerY - side / 2f).toInt().coerceIn(0, source.height - side)
            Bitmap.createBitmap(source, left, top, side, side)
        } finally {
            detector.close()
        }
    }

    private fun blendFace(output: Bitmap, swapped: Bitmap, left: Int, top: Int) {
        val canvas = Canvas(output)
        val mask = Bitmap.createBitmap(swapped.width, swapped.height, Bitmap.Config.ALPHA_8)
        val maskCanvas = Canvas(mask)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        maskCanvas.drawOval(
            swapped.width * 0.04f,
            swapped.height * 0.02f,
            swapped.width * 0.96f,
            swapped.height * 0.99f,
            maskPaint
        )

        canvas.saveLayer(
            left.toFloat(), top.toFloat(),
            (left + swapped.width).toFloat(), (top + swapped.height).toFloat(), null
        )
        canvas.drawBitmap(swapped, left.toFloat(), top.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        val maskPaintOnCanvas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, left.toFloat(), top.toFloat(), maskPaintOnCanvas)
        maskPaintOnCanvas.xfermode = null
        canvas.restore()
        mask.recycle()
    }

    private fun runArcFace(face: Bitmap): FloatArray {
        val session = embedder ?: error("ArcFace session is not ready")
        val pixels = bitmapToRgb(face, 112, 112)
        val tensorData = RgbTensorCodec.arcFace(pixels, 112, 112)
        val inputName = session.inputNames.firstOrNull() ?: error("ArcFace model has no input")
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensorData), longArrayOf(1, 3, 112, 112)).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                return flattenFloat(result[0].value, 512)
            }
        }
    }

    private fun runSwapper(session: OrtSession, target: Bitmap, latent: FloatArray): Bitmap {
        val targetData = RgbTensorCodec.swapper(bitmapToRgb(target, 128, 128), 128, 128)
        val names = session.inputNames.toList()
        require(names.size >= 2) { "INSwapper model must have target and source inputs" }
        val targetName = names.firstOrNull { name ->
            val lower = name.lowercase()
            lower.contains("target") || lower.contains("img")
        } ?: names[0]
        val latentName = names.firstOrNull { it != targetName } ?: names[1]

        OnnxTensor.createTensor(environment, FloatBuffer.wrap(targetData), longArrayOf(1, 3, 128, 128)).use { targetTensor ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(latent), longArrayOf(1, 512)).use { latentTensor ->
                session.run(mapOf(targetName to targetTensor, latentName to latentTensor)).use { result ->
                    val output = flattenFloat(result[0].value, 3 * 128 * 128)
                    return outputToBitmap(output)
                }
            }
        }
    }

    private fun outputToBitmap(values: FloatArray): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(values.size / 3)
        val plane = 128 * 128
        for (i in pixels.indices) {
            val r = (values[i].coerceIn(0f, 1f) * 255f).toInt()
            val g = (values[plane + i].coerceIn(0f, 1f) * 255f).toInt()
            val b = (values[plane * 2 + i].coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = Color.rgb(r, g, b)
        }
        bitmap.setPixels(pixels, 0, 128, 0, 0, 128, 128)
        return bitmap
    }

    private fun bitmapToRgb(source: Bitmap, width: Int, height: Int): FloatArray {
        val scaled = if (source.width == width && source.height == height) source
        else Bitmap.createScaledBitmap(source, width, height, true)
        val argb = IntArray(width * height)
        scaled.getPixels(argb, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        val rgb = FloatArray(argb.size * 3)
        for (i in argb.indices) {
            val pixel = argb[i]
            rgb[i * 3] = Color.red(pixel).toFloat()
            rgb[i * 3 + 1] = Color.green(pixel).toFloat()
            rgb[i * 3 + 2] = Color.blue(pixel).toFloat()
        }
        return rgb
    }

    private fun squareCrop(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        return Bitmap.createBitmap(source, left, top, side, side)
    }

    private fun loadEMap(file: java.io.File, inputDimension: Int, outputDimension: Int): FloatArray {
        val expected = inputDimension * outputDimension
        val bytes = file.readBytes()
        require(bytes.size >= expected * 4) { "EMAP is too small" }
        val floats = FloatArray(expected)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }

    private fun flattenFloat(value: Any?, expected: Int): FloatArray {
        val flat = when (value) {
            is FloatArray -> value
            is Array<*> -> flattenNested(value)
            else -> error("Unexpected ONNX output type: ${value?.javaClass?.name}")
        }
        require(flat.size >= expected) { "ONNX output has ${flat.size} values; expected $expected" }
        return flat.copyOf(expected)
    }

    private fun flattenNested(value: Array<*>): FloatArray {
        val out = ArrayList<Float>()
        fun append(item: Any?) {
            when (item) {
                is Float -> out += item
                is FloatArray -> item.forEach(out::add)
                is Array<*> -> item.forEach(::append)
                else -> error("Unexpected nested ONNX output type: ${item?.javaClass?.name}")
            }
        }
        value.forEach(::append)
        return out.toFloatArray()
    }
}
