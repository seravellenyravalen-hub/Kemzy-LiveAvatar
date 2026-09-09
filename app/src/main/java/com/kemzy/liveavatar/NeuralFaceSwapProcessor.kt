package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Android adaptation of the Deep-Live-Cam/InsightFace alignment + INSwapper path. */
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

    fun prepareAvatar(uri: String): String? {
        val source = context.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: return "Could not decode selected avatar"
        return try {
            val landmarks = detectSourceLandmarks(source)
                ?: return "No usable face landmarks found in the selected image"
            val aligned = FaceAlignment.align(source, landmarks, 112)
                ?: return "Could not align the selected face"
            try {
                sourceLatent = l2Normalize(runArcFace(aligned))
            } finally {
                aligned.recycle()
            }
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
        val session = swapper ?: return null
        if (tracking.faceCount <= 0 || tracking.landmarks.size < 3) return null
        val targetPoints = tracking.landmarks.toTypedArray()
        val alignment = FaceAlignment.affineToCanonical(targetPoints, 128) ?: return null
        val target128 = FaceAlignment.align(frame, targetPoints, 128) ?: return null
        return try {
            val swapped = runSwapper(session, target128, latent)
            try {
                val output = frame.copy(Bitmap.Config.ARGB_8888, true)
                pasteBackFeathered(output, swapped, alignment)
                output
            } finally {
                swapped.recycle()
            }
        } finally {
            target128.recycle()
        }
    }

    fun clear() { sourceLatent = null }

    private fun detectSourceLandmarks(source: Bitmap): Array<PointF>? {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
        return try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(source, 0)))
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
            listOfNotNull(
                face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
                face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
                face.getLandmark(FaceLandmark.NOSE_BASE)?.position,
                face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position,
                face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            ).map { PointF(it.x, it.y) }.takeIf { it.size >= 3 }?.toTypedArray()
        } finally {
            detector.close()
        }
    }

    private fun runArcFace(face: Bitmap): FloatArray {
        val session = embedder ?: error("ArcFace session is not ready")
        val data = RgbTensorCodec.arcFace(bitmapToRgb(face, 112, 112), 112, 112)
        val name = session.inputNames.firstOrNull() ?: error("ArcFace model has no input")
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, 112, 112)).use { tensor ->
            session.run(mapOf(name to tensor)).use { result ->
                return flattenFloat(result[0].value, 512)
            }
        }
    }

    private fun runSwapper(session: OrtSession, target: Bitmap, latent: FloatArray): Bitmap {
        val data = RgbTensorCodec.swapper(bitmapToRgb(target, 128, 128), 128, 128)
        val names = session.inputNames.toList()
        require(names.size >= 2) { "INSwapper model must have target and source inputs" }
        val targetName = names.firstOrNull { name ->
            val n = name.lowercase()
            n.contains("target") || n.contains("img") || n.contains("input")
        } ?: names[0]
        val latentName = names.firstOrNull { it != targetName } ?: names[1]
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, 128, 128)).use { targetTensor ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(latent), longArrayOf(1, 512)).use { latentTensor ->
                session.run(mapOf(targetName to targetTensor, latentName to latentTensor)).use { result ->
                    return outputToBitmap(flattenFloat(result[0].value, 3 * 128 * 128))
                }
            }
        }
    }

    private fun pasteBackFeathered(output: Bitmap, swapped: Bitmap, matrix: Matrix) {
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return
        val corners = floatArrayOf(0f, 0f, 128f, 0f, 128f, 128f, 0f, 128f)
        inverse.mapPoints(corners)
        val xs = floatArrayOf(corners[0], corners[2], corners[4], corners[6])
        val ys = floatArrayOf(corners[1], corners[3], corners[5], corners[7])
        val left = floor(xs.minOrNull() ?: 0f).toInt().coerceIn(0, output.width - 1)
        val top = floor(ys.minOrNull() ?: 0f).toInt().coerceIn(0, output.height - 1)
        val right = ceil(xs.maxOrNull() ?: 0f).toInt().coerceIn(left + 1, output.width)
        val bottom = ceil(ys.maxOrNull() ?: 0f).toInt().coerceIn(top + 1, output.height)
        val width = right - left
        val height = bottom - top

        val swapLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val local = Matrix(inverse).apply { postTranslate(-left.toFloat(), -top.toFloat()) }
        Canvas(swapLayer).drawBitmap(swapped, local, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        Canvas(mask).drawOval(width * .06f, height * .04f, width * .94f, height * .98f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 255 })

        val masked = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(masked).apply {
            drawBitmap(swapLayer, 0f, 0f, null)
            drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
        }
        Canvas(output).drawBitmap(masked, left.toFloat(), top.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        mask.recycle()
        swapLayer.recycle()
        masked.recycle()
    }

    private fun outputToBitmap(values: FloatArray): Bitmap {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(128 * 128)
        val plane = 128 * 128
        for (i in pixels.indices) {
            val r = (values[i].coerceIn(0f, 1f) * 255f).toInt()
            val g = (values[plane + i].coerceIn(0f, 1f) * 255f).toInt()
            val b = (values[plane * 2 + i].coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = android.graphics.Color.rgb(r, g, b)
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
            val p = argb[i]
            rgb[i * 3] = android.graphics.Color.red(p).toFloat()
            rgb[i * 3 + 1] = android.graphics.Color.green(p).toFloat()
            rgb[i * 3 + 2] = android.graphics.Color.blue(p).toFloat()
        }
        return rgb
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sum = 0f
        values.forEach { sum += it * it }
        val scale = 1f / max(kotlin.math.sqrt(sum.toDouble()).toFloat(), 1e-12f)
        return FloatArray(values.size) { values[it] * scale }
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
