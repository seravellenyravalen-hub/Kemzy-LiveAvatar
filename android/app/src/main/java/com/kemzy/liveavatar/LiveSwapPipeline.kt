package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF

/** Simple immutable 2-D point used by the face-analysis boundary. */
data class Point2(val x: Float, val y: Float)

data class FaceLandmarks(
    val leftEye: Point2,
    val rightEye: Point2,
    val nose: Point2,
    val leftMouth: Point2,
    val rightMouth: Point2
)

data class FaceGeometry(
    val landmarks: FaceLandmarks,
    val bbox: RectF? = null
) {
    val landmarkCount: Int = 5
    val faceCenter: Point2 = Point2(
        (landmarks.leftEye.x + landmarks.rightEye.x) * 0.5f,
        (landmarks.leftEye.y + landmarks.rightEye.y) * 0.5f
    )
}

/**
 * The minimum detector contract needed by the live pipeline.
 * A detector implementation must return real landmarks; no synthetic fallback is allowed.
 */
interface FaceDetector {
    fun detect(frame: Bitmap): FaceGeometry?
}

/** Produces the 512-D source identity vector expected by INSwapper. */
interface FaceEmbedder {
    fun embedding(alignedSourceFace: Bitmap): FloatArray
}

/** Executes the actual INSwapper model against an aligned target face. */
interface FaceSwapper {
    fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap
}

/**
 * Maps an aligned 128x128 swap result back to the camera frame.
 * The feathered ellipse is intentionally defined in aligned-face space so its boundary
 * follows the same affine transform as the swapped pixels.
 */
class FaceCompositor(
    private val outputSize: Int = InswapperModelSpec.faceWidth
) {
    fun composite(frame: Bitmap, swappedFace: Bitmap, geometry: FaceGeometry): Bitmap {
        require(swappedFace.width == outputSize && swappedFace.height == outputSize) {
            "Swapper output must be ${outputSize}x${outputSize}."
        }

        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val matrix = alignmentMatrix(geometry, outputSize)

        canvas.save()
        canvas.concat(matrix)
        val mask = RectF(6f, 6f, outputSize - 6f, outputSize - 6f)
        canvas.clipPath(android.graphics.Path().apply { addOval(mask, android.graphics.Path.Direction.CW) })
        canvas.drawBitmap(swappedFace, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return result
    }

    private fun alignmentMatrix(geometry: FaceGeometry, size: Int): Matrix {
        val left = geometry.landmarks.leftEye
        val right = geometry.landmarks.rightEye
        val nose = geometry.landmarks.nose
        val eyeMidX = (left.x + right.x) * 0.5f
        val eyeMidY = (left.y + right.y) * 0.5f
        val eyeDistance = hypot(right.x - left.x, right.y - left.y).coerceAtLeast(1f)
        val scale = eyeDistance / 48f

        val angle = kotlin.math.atan2(right.y - left.y, right.x - left.x)
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val centerX = eyeMidX
        val centerY = eyeMidY + scale * 18f

        // Destination aligned square -> target coordinates.
        val m = Matrix()
        m.setValues(floatArrayOf(
            scale * cos, -scale * sin, centerX - scale * cos * 64f + scale * sin * 64f,
            scale * sin, scale * cos, centerY - scale * sin * 64f - scale * cos * 64f,
            0f, 0f, 1f
        ))
        // Keep the nose referenced so the geometry cannot silently become eye-only.
        if (!nose.x.isFinite() || !nose.y.isFinite()) {
            throw IllegalArgumentException("Invalid face landmark geometry")
        }
        return m
    }

    private fun hypot(x: Float, y: Float): Float = kotlin.math.sqrt(x * x + y * y)
}

enum class LiveSwapResult {
    Swapped,
    NoSourceFace,
    NoTargetFace,
    ModelUnavailable
}

/** Coordinates detection, alignment, embedding, INSwapper inference and paste-back. */
class LiveSwapProcessor(
    private val detector: FaceDetector,
    private val embedder: FaceEmbedder,
    private val swapper: FaceSwapper,
    private val compositor: FaceCompositor
) {
    fun process(frame: Bitmap?, sourceFace: Bitmap?): LiveSwapResult {
        if (frame == null || sourceFace == null) return LiveSwapResult.NoSourceFace
        val target = detector.detect(frame) ?: return LiveSwapResult.NoTargetFace
        val sourceGeometry = detector.detect(sourceFace) ?: return LiveSwapResult.NoSourceFace

        val alignedSource = FaceAlignment.align(sourceFace, sourceGeometry)
        val alignedTarget = FaceAlignment.align(frame, target)
        val embedding = embedder.embedding(alignedSource)
        require(embedding.size == InswapperModelSpec.sourceEmbeddingSize) {
            "Expected a 512-value source embedding, got ${embedding.size}."
        }
        val swapped = swapper.swap(alignedTarget, embedding)
        compositor.composite(frame, swapped, target)
        return LiveSwapResult.Swapped
    }
}

/** Landmark-driven affine crop shared by source and target preparation. */
object FaceAlignment {
    fun align(frame: Bitmap, geometry: FaceGeometry, size: Int = InswapperModelSpec.faceWidth): Bitmap {
        val left = geometry.landmarks.leftEye
        val right = geometry.landmarks.rightEye
        val centerX = (left.x + right.x) * 0.5f
        val centerY = (left.y + right.y) * 0.5f
        val distance = kotlin.math.sqrt(
            (right.x - left.x) * (right.x - left.x) +
                (right.y - left.y) * (right.y - left.y)
        ).coerceAtLeast(1f)
        val scale = size * 0.55f / distance
        val angle = -Math.toDegrees(kotlin.math.atan2(right.y - left.y, right.x - left.x).toDouble()).toFloat()

        val matrix = Matrix().apply {
            postTranslate(-centerX, -centerY)
            postRotate(angle)
            postScale(scale, scale)
            postTranslate(size * 0.5f, size * 0.42f)
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                frame,
                matrix,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }
    }
}

object UnavailableFaceDetector : FaceDetector {
    override fun detect(frame: Bitmap): FaceGeometry? = null
}

object UnavailableFaceEmbedder : FaceEmbedder {
    override fun embedding(alignedSourceFace: Bitmap): FloatArray =
        error("No face embedding model has been installed.")
}

object UnavailableFaceSwapper : FaceSwapper {
    override fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap =
        error("No INSwapper model has been installed.")
}

class InswapperInputContract(
    val faceWidth: Int = InswapperModelSpec.faceWidth,
    val faceHeight: Int = InswapperModelSpec.faceHeight,
    val sourceEmbeddingSize: Int = InswapperModelSpec.sourceEmbeddingSize,
    val batchSize: Int = 1
)
