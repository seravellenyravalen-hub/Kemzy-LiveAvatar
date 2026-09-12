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
import kotlin.math.max
import kotlin.math.min

/** Device-side, file-backed LivePortrait runtime. */
class LivePortraitEngine(private val bundle: LiveModelBundle) : Closeable {
    private lateinit var appearance: OnnxInferenceEngine
    private lateinit var motion: OnnxInferenceEngine
    private lateinit var stitching: OnnxInferenceEngine
    private lateinit var warping: OnnxInferenceEngine
    private var sourceFeature: FloatTensor? = null
    private var sourceMotion: MotionState? = null
    private var sourceBitmap: Bitmap? = null
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
        initialized = true
    }

    fun process(frame: Bitmap, face: Face): Bitmap? {
        if (!initialized) return null
        val driverCrop = FaceCrop.square(frame, face, 2.3f, 256)
        return try {
            val driverMotion = motion.inferMotion(driverCrop)
            val kpSource = requireNotNull(sourceMotion).keypoints
            val stitched = stitching.inferStitch(kpSource, driverMotion.keypoints)
            val generated = warping.inferWarp(requireNotNull(sourceFeature), stitched, kpSource)
            composite(frame, generated, FaceCrop.squareRect(frame, face, 2.3f))
        } finally {
            if (!driverCrop.isRecycled) driverCrop.recycle()
        }
    }

    override fun close() {
        initialized = false
        sourceFeature = null
        sourceMotion = null
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
        canvas.save(); canvas.clipRect(mask); canvas.drawBitmap(generated, null, mask, paint); canvas.restore()
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
    val keypoints: FloatArray
)

private fun OnnxInferenceEngine.inferImage(bitmap: Bitmap): FloatTensor {
    val input = bitmap.toNchwFloat()
    return runTensor(input.values, input.shape).first()
}

private fun OnnxInferenceEngine.inferMotion(bitmap: Bitmap): MotionState {
    val result = runTensor(bitmap.toNchwFloat().values, longArrayOf(1, 3, 256, 256))
    require(result.size >= 7) { "Motion extractor returned ${result.size} outputs; expected at least 7." }
    return MotionState(
        headpose(result[0].values), headpose(result[1].values), headpose(result[2].values),
        result[3].values.copyOf(), result[4].values.copyOf(), result[5].values.first(),
        result[6].values.reshapeKeypoints()
    )
}

private fun OnnxInferenceEngine.inferStitch(source: FloatArray, driving: FloatArray): FloatArray {
    val joined = FloatArray(source.size + driving.size)
    source.copyInto(joined); driving.copyInto(joined, source.size)
    val delta = runTensor(joined, longArrayOf(1, joined.size.toLong())).first().values
    val kpCount = source.size / 3
    val out = driving.copyOf()
    val expressionCount = min(kpCount * 3, delta.size)
    for (i in 0 until expressionCount) out[i] += delta[i]
    if (delta.size >= expressionCount + 2) {
        for (i in 0 until kpCount) { out[i * 3] += delta[expressionCount]; out[i * 3 + 1] += delta[expressionCount + 1] }
    }
    return out
}

private fun OnnxInferenceEngine.inferWarp(feature: FloatTensor, driving: FloatArray, source: FloatArray): Bitmap {
    val result = runMultiTensor(listOf(
        feature.values to feature.shape,
        driving to longArrayOf(1, driving.size.toLong()),
        source to longArrayOf(1, source.size.toLong())
    )).first()
    require(result.shape.size == 4 && result.shape[0] == 1L && result.shape[1] == 3L) {
        "Warping model must return 1x3xHxW, got ${result.shape.contentToString()}"
    }
    val h = result.shape[2].toInt(); val w = result.shape[3].toInt(); val plane = h * w
    val pixels = IntArray(plane)
    for (i in pixels.indices) {
        val r = (result.values[i].coerceIn(0f, 1f) * 255f).toInt()
        val g = (result.values[plane + i].coerceIn(0f, 1f) * 255f).toInt()
        val b = (result.values[2 * plane + i].coerceIn(0f, 1f) * 255f).toInt()
        pixels[i] = -0x1000000 or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun OnnxInferenceEngine.runTensor(values: FloatArray, shape: LongArray): List<FloatTensor> =
    runMultiTensor(listOf(values to shape))

private fun OnnxInferenceEngine.runMultiTensor(inputs: List<Pair<FloatArray, LongArray>>): List<FloatTensor> {
    val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
    val tensors = inputs.map { OnnxTensor.createTensor(env, it.first, it.second) }
    return try {
        val names = inputNames().toList()
        require(names.size >= tensors.size) { "Model expects ${names.size} inputs but runtime supplied ${tensors.size}." }
        val feed = names.take(tensors.size).mapIndexed { i, name -> name to tensors[i] }.toMap()
        run(feed).use { result ->
            (0 until result.size).map { index -> (result[index].value as? OnnxTensor)?.flattenTensor()
                ?: error("ONNX output $index is not a tensor") }
        }
    } finally { tensors.forEach { it.close() } }
}

private fun OnnxTensor.flattenTensor(): FloatTensor {
    val shape = info.shape
    val count = shape.fold(1L) { a, b -> a * b }.toInt()
    val out = FloatArray(count)
    fun copy(v: Any?, offset: Int): Int = when (v) {
        is FloatArray -> { v.copyInto(out, offset); v.size }
        is FloatBuffer -> { val dup = v.duplicate(); dup.get(out, offset, dup.remaining()); dup.remaining() }
        is Array<*> -> { var p = offset; v.forEach { p += copy(it, p) }; p - offset }
        is Number -> { out[offset] = v.toFloat(); 1 }
        else -> error("Unsupported ONNX tensor value ${v?.javaClass}")
    }
    copy(value, 0)
    return FloatTensor(out, shape)
}

private fun FloatArray.reshapeKeypoints(): FloatArray =
    if (size % 3 == 0) copyOf() else error("Expected keypoint tensor divisible by 3, got $size")

private fun headpose(values: FloatArray): Float {
    if (values.size != 66) return values.firstOrNull() ?: 0f
    var sum = 0.0; var weighted = 0.0
    for (i in values.indices) { val e = kotlin.math.exp(values[i].toDouble()); sum += e; weighted += e * i }
    return ((weighted / sum) * 3.0 - 97.5).toFloat()
}

private data class Nchw(val values: FloatArray, val shape: LongArray)
private fun Bitmap.toNchwFloat(): Nchw {
    val bmp = if (width == 256 && height == 256) this else Bitmap.createScaledBitmap(this, 256, 256, true)
    val pixels = IntArray(256 * 256); bmp.getPixels(pixels, 0, 256, 0, 0, 256, 256)
    val out = FloatArray(3 * 256 * 256); val plane = 256 * 256
    for (i in pixels.indices) { out[i] = ((pixels[i] shr 16 and 255) / 255f); out[plane + i] = ((pixels[i] shr 8 and 255) / 255f); out[2 * plane + i] = ((pixels[i] and 255) / 255f) }
    if (bmp !== this) bmp.recycle()
    return Nchw(out, longArrayOf(1, 3, 256, 256))
}

private object FaceCrop {
    fun square(source: Bitmap, face: Face?, scale: Float, size: Int): Bitmap {
        val rect = squareRect(source, face, scale)
        val crop = Bitmap.createBitmap(source, rect.left.toInt(), rect.top.toInt(), max(1, rect.width().toInt()), max(1, rect.height().toInt()))
        return Bitmap.createScaledBitmap(crop, size, size, true).also { if (it !== crop) crop.recycle() }
    }
    fun squareRect(source: Bitmap, face: Face?, scale: Float): RectF {
        val box = face?.boundingBox ?: Rect(0, 0, source.width, source.height)
        val cx = box.centerX().toFloat(); val cy = box.centerY().toFloat()
        val side = max(box.width(), box.height()).toFloat() * scale
        val left = (cx - side / 2f).coerceIn(0f, max(0f, source.width - side))
        val top = (cy - side / 2f).coerceIn(0f, max(0f, source.height - side))
        val actual = min(side, min(source.width - left, source.height - top))
        return RectF(left, top, left + actual, top + actual)
    }
}
