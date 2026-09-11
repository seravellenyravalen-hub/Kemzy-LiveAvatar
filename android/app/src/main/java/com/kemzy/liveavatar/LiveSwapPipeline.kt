package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.TimeUnit

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

interface FaceDetector {
    fun detect(frame: Bitmap): FaceGeometry?
}

class MlKitFaceDetector : FaceDetector, AutoCloseable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    override fun detect(frame: Bitmap): FaceGeometry? {
        val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)), 500L, TimeUnit.MILLISECONDS)
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val leftMouth = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val rightMouth = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null
        return FaceGeometry(
            FaceLandmarks(
                Point2(leftEye.x, leftEye.y), Point2(rightEye.x, rightEye.y),
                Point2(nose.x, nose.y), Point2(leftMouth.x, leftMouth.y),
                Point2(rightMouth.x, rightMouth.y)
            ),
            RectF(face.boundingBox)
        )
    }

    override fun close() = detector.close()
}

interface FaceEmbedder {
    fun embedding(alignedSourceFace: Bitmap): FloatArray
}

interface FaceSwapper {
    fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap
}

class FaceCompositor(private val outputSize: Int = InswapperModelSpec.faceWidth) {
    fun composite(frame: Bitmap, swappedFace: Bitmap, geometry: FaceGeometry): Bitmap {
        require(swappedFace.width == outputSize && swappedFace.height == outputSize) {
            "Swapper output must be ${outputSize}x${outputSize}."
        }
        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        canvas.save()
        canvas.concat(targetMatrix(geometry, outputSize))
        val mask = RectF(5f, 5f, outputSize - 5f, outputSize - 5f)
        canvas.clipPath(android.graphics.Path().apply { addOval(mask, android.graphics.Path.Direction.CW) })
        canvas.drawBitmap(swappedFace, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return result
    }

    private fun targetMatrix(geometry: FaceGeometry, size: Int): Matrix {
        val l = geometry.landmarks
        val source = floatArrayOf(l.leftEye.x, l.leftEye.y, l.rightEye.x, l.rightEye.y, l.nose.x, l.nose.y, l.leftMouth.x, l.leftMouth.y)
        val scale = size / 112f
        val canonical = floatArrayOf(
            38.2946f * scale, 51.6963f * scale,
            73.5318f * scale, 51.5014f * scale,
            56.0252f * scale, 71.7366f * scale,
            41.5493f * scale, 92.3655f * scale
        )
        return Matrix().also {
            check(it.setPolyToPoly(canonical, 0, source, 0, 4)) { "Unable to map the aligned face back to the camera frame." }
        }
    }
}

enum class LiveSwapStatus { Swapped, NoSourceFace, NoTargetFace, ModelUnavailable }

data class LiveSwapOutput(val status: LiveSwapStatus, val frame: Bitmap? = null)

class LiveSwapProcessor(
    private val detector: FaceDetector,
    private val embedder: FaceEmbedder,
    private val swapper: FaceSwapper,
    private val compositor: FaceCompositor
) {
    private var preparedSource: Bitmap? = null
    private var preparedEmbedding: FloatArray? = null

    /** Detects and embeds the selected source exactly once until the source bitmap changes. */
    fun prepareSource(sourceFace: Bitmap?): LiveSwapStatus {
        if (sourceFace == null) {
            preparedSource = null
            preparedEmbedding = null
            return LiveSwapStatus.NoSourceFace
        }
        if (preparedSource === sourceFace && preparedEmbedding != null) return LiveSwapStatus.Swapped
        val geometry = detector.detect(sourceFace) ?: return LiveSwapStatus.NoSourceFace
        val alignedSource = FaceAlignment.align(sourceFace, geometry, 112)
        val embedding = embedder.embedding(alignedSource)
        require(embedding.size == InswapperModelSpec.sourceEmbeddingSize) {
            "Expected a 512-value source embedding, got ${embedding.size}."
        }
        preparedSource = sourceFace
        preparedEmbedding = embedding
        return LiveSwapStatus.Swapped
    }

    fun process(frame: Bitmap?, sourceFace: Bitmap?): LiveSwapOutput {
        if (frame == null || sourceFace == null) return LiveSwapOutput(LiveSwapStatus.NoSourceFace)
        val sourceStatus = prepareSource(sourceFace)
        if (sourceStatus != LiveSwapStatus.Swapped) return LiveSwapOutput(sourceStatus)
        val target = detector.detect(frame) ?: return LiveSwapOutput(LiveSwapStatus.NoTargetFace)
        val alignedTarget = FaceAlignment.align(frame, target, InswapperModelSpec.faceWidth)
        val swapped = swapper.swap(alignedTarget, preparedEmbedding!!)
        return LiveSwapOutput(LiveSwapStatus.Swapped, compositor.composite(frame, swapped, target))
    }
}

object FaceAlignment {
    fun align(frame: Bitmap, geometry: FaceGeometry, size: Int = InswapperModelSpec.faceWidth): Bitmap {
        val l = geometry.landmarks
        val source = floatArrayOf(l.leftEye.x, l.leftEye.y, l.rightEye.x, l.rightEye.y, l.nose.x, l.nose.y, l.leftMouth.x, l.leftMouth.y)
        val target = floatArrayOf(
            38.2946f * size / 112f, 51.6963f * size / 112f,
            73.5318f * size / 112f, 51.5014f * size / 112f,
            56.0252f * size / 112f, 71.7366f * size / 112f,
            41.5493f * size / 112f, 92.3655f * size / 112f
        )
        val matrix = Matrix()
        check(matrix.setPolyToPoly(source, 0, target, 0, 4)) { "Unable to align the detected face." }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(frame, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }
}

object UnavailableFaceDetector : FaceDetector { override fun detect(frame: Bitmap): FaceGeometry? = null }
object UnavailableFaceEmbedder : FaceEmbedder { override fun embedding(alignedSourceFace: Bitmap): FloatArray = error("No face embedding model has been installed.") }
object UnavailableFaceSwapper : FaceSwapper { override fun swap(alignedTargetFace: Bitmap, sourceEmbedding: FloatArray): Bitmap = error("No INSwapper model has been installed.") }

class InswapperInputContract(
    val faceWidth: Int = InswapperModelSpec.faceWidth,
    val faceHeight: Int = InswapperModelSpec.faceHeight,
    val sourceEmbeddingSize: Int = InswapperModelSpec.sourceEmbeddingSize,
    val batchSize: Int = 1
)
