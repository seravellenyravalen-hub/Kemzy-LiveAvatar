package com.kemzy.liveavatar.liveportrait

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import com.google.mlkit.vision.face.Face
import com.kemzy.liveavatar.LiveModelBundle
import com.kemzy.liveavatar.OnnxInferenceEngine
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** File-backed LivePortrait runtime. Sessions are created from model paths, not byte arrays. */
class LivePortraitEngine(private val bundle: LiveModelBundle) : Closeable {
    private lateinit var appearance: OnnxInferenceEngine
    private lateinit var motion: OnnxInferenceEngine
    private lateinit var stitching: OnnxInferenceEngine
    private lateinit var warping: OnnxInferenceEngine
    private var sourceFeature: FloatTensor? = null
    private var sourceMotion: MotionState? = null
    private var sourceBitmap: Bitmap? = null
    private var driverZero: MotionState? = null
    private var initialized = false

    fun initialize(source: Bitmap, sourceFace: Face) {
        check(bundle.isComplete) { "LivePortrait model bundle is incomplete: ${bundle.missingRoles().joinToString()}" }
        closeSessionsOnly()
        appearance = OnnxInferenceEngine.fromFile(requireNotNull(bundle.appearance), "appearance feature extractor")
        motion = OnnxInferenceEngine.fromFile(requireNotNull(bundle.motion), "motion extractor")
        stitching = OnnxInferenceEngine.fromFile(requireNotNull(bundle.stitching), "stitching")
        warping = OnnxInferenceEngine.fromFile(requireNotNull(bundle.warpingSpade), "warping SPADE")

        val crop = FaceCrop.square(source, sourceFace, 2.3f, 256)
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = crop
        sourceFeature = appearance.inferImage(crop)
        sourceMotion = motion.inferMotion(crop)
        driverZero = null
        initialized = true
    }

    fun process(frame: Bitmap, face: Face): Bitmap? {
        if (!initialized) return null
        val driverCrop = FaceCrop.square(frame, face, 2.3f, 256)
        return try {
            val driver = motion.inferMotion(driverCrop)
            val source = requireNotNull(sourceMotion)
            val zero = driverZero ?: driver.also { driverZero = it }
            val driving = relativeKeypoints(source, zero, driver)
            val stitched = stitching.inferStitch(source.transformedKeypoints, driving)
            val generated = warping.inferWarp(requireNotNull(sourceFeature), stitched, source.transformedKeypoints)
            composite(frame, generated, FaceCrop.squareRect(frame, face, 2.3f))
        } finally {
            if (!driverCrop.isRecycled) driverCrop.recycle()
        }
    }

    override fun close() {
        initialized = false
        sourceFeature = null
        sourceMotion = null
        driverZero = null
        sourceBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = null
        closeSessionsOnly()
    }

    private fun closeSessionsOnly() {
        runCatching { if (::appearance.isInitialized) appearance.close() }
        runCatching { if (::motion.isInitialized) motion.close() }
        runCatching { if (::stitching.isInitialized) stitching.close() }
        runCatching { if (::warping.isInitialized) warping.close() }
    }

    private fun composite(frame: Bitmap, generated: Bitmap, target: RectF): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val pad = max(8f, target.width() * 0.04f)
        val mask = RectF(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
        canvas.save()
        canvas.clipRect(mask)
        canvas.drawBitmap(generated, null, mask, paint)
        canvas.restore()
        if (!generated.isRecycled) generated.recycle()
        return out
    }
}

data class FloatTensor(val values: FloatArray, val shape: LongArray)

data class MotionState(
    val pitch: Float,
    val yaw: Float,
    val roll: Float,
    val translation: FloatArray,
    val expression: FloatArray,
    val scale: Float,
    val keypoints: FloatArray,
    val rotation: FloatArray,
    val transformedKeypoints: FloatArray
)

private fun relativeKeypoints(source: MotionState, zero: MotionState, driver: MotionState): FloatArray {
    val rNew = mul3(mul3(driver.rotation, transpose3(zero.rotation)), source.rotation)
    val count = source.keypoints.size / 3
    val out = FloatArray(source.keypoints.size)
    val scale = driver.scale / zero.scale.coerceAtLeast(1e-6f)
    for (i in 0 until count) {
        val b = i * 3
        val x = source.keypoints[b]
        val y = source.keypoints[b + 1]
        val z = source.keypoints[b + 2]
        val dx = x * rNew[0] + y * rNew[3] + z * rNew[6]
        val dy = x * rNew[1] + y * rNew[4] + z * rNew[7]
        val dz = x * rNew[2] + y * rNew[5] + z * rNew[8]
        out[b] = scale * (dx + source.expression.getOrElse(b) { 0f } + driver.expression.getOrElse(b) { 0f } - zero.expression.getOrElse(b) { 0f }) + source.translation.getOrElse(0) { 0f } + driver.translation.getOrElse(0) { 0f } - zero.translation.getOrElse(0) { 0f }
        out[b + 1] = scale * (dy + source.expression.getOrElse(b + 1) { 0f } + driver.expression.getOrElse(b + 1) { 0f } - zero.expression.getOrElse(b + 1) { 0f }) + source.translation.getOrElse(1) { 0f } + driver.translation.getOrElse(1) { 0f } - zero.translation.getOrElse(1) { 0f }
        out[b + 2] = scale * (dz + source.expression.getOrElse(b + 2) { 0f } + driver.expression.getOrElse(b + 2) { 0f } - zero.expression.getOrElse(b + 2) { 0f })
    }
    return out
}

private fun OnnxInferenceEngine.inferImage(bitmap: Bitmap): FloatTensor {
    val input = bitmap.toNchwFloat()
    return runTensor(input.values, input.shape).first()
}

private fun OnnxInferenceEngine.inferMotion(bitmap: Bitmap): MotionState {
    val input = bitmap.toNchwFloat()
    val result = runTensor(input.values, input.shape)
    require(result.size >= 7) { "Motion extractor returned ${result.size} outputs; expected at least 7." }
    val pitch = headpose(result[0].values)
    val yaw = headpose(result[1].values)
    val roll = headpose(result[2].values)
    val translation = result[3].values.copyOf()
    val expression = result[4].values.copyOf()
    val scale = result[5].values.firstOrNull() ?: error("Motion extractor scale output is empty")
    val keypoints = result[6].values.reshapeKeypoints()
    val rotation = rotationMatrix(pitch, yaw, roll)
    val transformed = transformKeypoints(rotation, translation, expression, scale, keypoints)
    return MotionState(pitch, yaw, roll, translation, expression, scale, keypoints, rotation, transformed)
}

private fun transformKeypoints(rotation: FloatArray, translation: FloatArray, expression: FloatArray, scale: Float, keypoints: FloatArray): FloatArray {
    val out = FloatArray(keypoints.size)
    for (i in keypoints.indices step 3) {
        val x = keypoints[i]
        val y = keypoints[i + 1]
        val z = keypoints[i + 2]
        out[i] = scale * (x * rotation[0] + y * rotation[3] + z * rotation[6] + expression.getOrElse(i) { 0f }) + translation.getOrElse(0) { 0f }
        out[i + 1] = scale * (x * rotation[1] + y * rotation[4] + z * rotation[7] + expression.getOrElse(i + 1) { 0f }) + translation.getOrElse(1) { 0f }
        out[i + 2] = scale * (x * rotation[2] + y * rotation[5] + z * rotation[8] + expression.getOrElse(i + 2) { 0f })
    }
    return out
}

private fun rotationMatrix(pitch: Float, yaw: Float, roll: Float): FloatArray {
    val p = Math.toRadians(pitch.toDouble())
    val y = Math.toRadians(yaw.toDouble())
    val r = Math.toRadians(roll.toDouble())
    val rx = floatArrayOf(1f, 0f, 0f, 0f, cos(p).toFloat(), (-sin(p)).toFloat(), 0f, sin(p).toFloat(), cos(p).toFloat())
    val ry = floatArrayOf(cos(y).toFloat(), 0f, sin(y).toFloat(), 0f, 1f, 0f, (-sin(y)).toFloat(), 0f, cos(y).toFloat())
    val rz = floatArrayOf(cos(r).toFloat(), (-sin(r)).toFloat(), 0f, sin(r).toFloat(), cos(r).toFloat(), 0f, 0f, 0f, 1f)
    return transpose3(mul3(mul3(rz, ry), rx))
}

private fun mul3(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9) { i ->
    val row = i / 3
    val col = i % 3
    a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
}

private fun transpose3(a: FloatArray): FloatArray = FloatArray(9) { i -> a[(i % 3) * 3 + i / 3] }

private fun OnnxInferenceEngine.inferStitch(source: FloatArray, driving: FloatArray): FloatArray {
    val joined = FloatArray(source.size + driving.size)
    source.copyInto(joined)
    driving.copyInto(joined, source.size)
    val delta = runTensor(joined, longArrayOf(1L, joined.size.toLong())).first().values
    val out = driving.copyOf()
    val n = min(out.size, delta.size)
    for (i in 0 until n) out[i] += delta[i]
    return out
}

private fun OnnxInferenceEngine.inferWarp(feature: FloatTensor, driving: FloatArray, source: FloatArray): Bitmap {
    val inputs = listOf(
        feature.values to feature.shape,
        driving to longArrayOf(1L, driving.size.toLong()),
        source to longArrayOf(1L, source.size.toLong())
    )
    val result = runMultiTensor(inputs).first()
    require(result.shape.size == 4 && result.shape[0] == 1L && result.shape[1] == 3L) {
        "Warping model must return 1x3xHxW, got ${result.shape.contentToString()}"
    }
    val h = result.shape[2].toInt()
    val w = result.shape[3].toInt()
    val plane = h * w
    val pixels = IntArray(plane)
    for (i in pixels.indices) {
        val r = (result.values[i].coerceIn(0f, 1f) * 255f).toInt()
        val g = (result.values[plane + i].coerceIn(0f, 1f) * 255f).toInt()
        val b = (result.values[2 * plane + i].coerceIn(0f, 1f) * 255f).toInt()
        pixels[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun OnnxInferenceEngine.runTensor(values: FloatArray, shape: LongArray): List<FloatTensor> = runMultiTensor(listOf(values to shape))

private fun OnnxInferenceEngine.runMultiTensor(inputs: List<Pair<FloatArray, LongArray>>): List<FloatTensor> {
    val tensors = inputs.map { (values, shape) -> OnnxTensor.createTensor(environment(), FloatBuffer.wrap(values), shape) }
    return try {
        val names = inputNames().toList()
        require(names.size == tensors.size) { "Model expects ${names.size} inputs but runtime supplied ${tensors.size}." }
        val feed = names.mapIndexed { i, name -> name to tensors[i] }.toMap()
        run(feed).use { result ->
            (0 until result.size()).map { index ->
                flattenValue(result[index].value)
            }
        }
    } finally {
        tensors.forEach { it.close() }
    }
}

private fun flattenValue(value: Any?): FloatTensor {
    require(value != null) { "ONNX output value is null" }
    val shape = when (value) {
        is FloatArray -> longArrayOf(value.size.toLong())
        is FloatBuffer -> longArrayOf(value.remaining().toLong())
        else -> longArrayOf(flattenCount(value).toLong())
    }
    val out = FloatArray(shape[0].toInt())
    var offset = 0
    fun copy(v: Any?) {
        when (v) {
            is FloatArray -> { v.copyInto(out, offset); offset += v.size }
            is FloatBuffer -> { val d = v.duplicate(); val n = d.remaining(); d.get(out, offset, n); offset += n }
            is Array<*> -> v.forEach(::copy)
            is Number -> { out[offset++] = v.toFloat() }
            else -> error("Unsupported ONNX output type: ${v.javaClass.name}")
        }
    }
    copy(value)
    return FloatTensor(out, shape)
}

private fun flattenCount(value: Any?): Int = when (value) {
    is FloatArray -> value.size
    is FloatBuffer -> value.remaining()
    is Array<*> -> value.sumOf { flattenCount(it) }
    is Number -> 1
    else -> error("Unsupported ONNX output type: ${value?.javaClass?.name ?: "null"}")
}

private fun FloatArray.reshapeKeypoints(): FloatArray = if (size % 3 == 0) copyOf() else error("Expected keypoint tensor divisible by 3, got $size")

private fun headpose(v: FloatArray): Float {
    if (v.size != 66) return v.firstOrNull() ?: 0f
    var sum = 0.0
    var weighted = 0.0
    for (i in v.indices) {
        val e = exp(v[i].toDouble())
        sum += e
        weighted += e * i
    }
    return ((weighted / sum) * 3.0 - 97.5).toFloat()
}

private data class Nchw(val values: FloatArray, val shape: LongArray)

private fun Bitmap.toNchwFloat(): Nchw {
    val bmp = if (width == 256 && height == 256) this else Bitmap.createScaledBitmap(this, 256, 256, true)
    val pixels = IntArray(256 * 256)
    bmp.getPixels(pixels, 0, 256, 0, 0, 256, 256)
    val values = FloatArray(3 * 256 * 256)
    val plane = 256 * 256
    for (i in pixels.indices) {
        values[i] = (((pixels[i] shr 16) and 255) - 127.5f) / 127.5f
        values[plane + i] = (((pixels[i] shr 8) and 255) - 127.5f) / 127.5f
        values[2 * plane + i] = ((pixels[i] and 255) - 127.5f) / 127.5f
    }
    if (bmp !== this) bmp.recycle()
    return Nchw(values, longArrayOf(1L, 3L, 256L, 256L))
}

private object FaceCrop {
    fun square(source: Bitmap, face: Face, scale: Float, size: Int): Bitmap {
        val rect = squareRect(source, face, scale)
        val crop = Bitmap.createBitmap(source, rect.left.toInt(), rect.top.toInt(), max(1, rect.width().toInt()), max(1, rect.height().toInt()))
        return Bitmap.createScaledBitmap(crop, size, size, true).also { if (it !== crop) crop.recycle() }
    }

    fun squareRect(source: Bitmap, face: Face?, scale: Float): RectF {
        val b = face?.boundingBox ?: Rect(0, 0, source.width, source.height)
        val cx = b.centerX().toFloat()
        val cy = b.centerY().toFloat()
        val side = max(b.width(), b.height()).toFloat() * scale
        val left = (cx - side / 2f).coerceIn(0f, max(0f, source.width - side))
        val top = (cy - side / 2f).coerceIn(0f, max(0f, source.height - side))
        val actual = min(side, min(source.width - left, source.height - top))
        return RectF(left, top, left + actual, top + actual)
    }
}
