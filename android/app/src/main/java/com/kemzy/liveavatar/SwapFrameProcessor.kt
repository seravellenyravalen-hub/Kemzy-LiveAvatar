package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

interface FaceDetector {
    fun detect(frame: Bitmap): List<DetectedFace>
}

data class DetectedFace(
    val box: FloatArray,
    /** Five points in image coordinates: left eye, right eye, nose, left mouth, right mouth. */
    val landmarks: FloatArray
) {
    init {
        require(box.size >= 4) { "Face box must contain x1,y1,x2,y2" }
        require(landmarks.size >= 10) { "Face landmarks must contain five x/y points" }
    }
}

interface FaceRecognizer {
    fun embedding(face: Bitmap): FloatArray
}

interface FaceSwapper {
    fun swap(alignedTarget: Bitmap, sourceEmbedding: FloatArray): Bitmap
}

/**
 * Real Android-side swap pipeline boundary: detection -> five-point alignment
 * -> ArcFace identity embedding -> 128x128 INSwapper -> inverse affine paste.
 * Model adapters must be backed by actual ONNX models; there is no fallback
 * animation or fake output.
 */
class DeepLiveCamFrameProcessor(
    private val detector: FaceDetector,
    private val recognizer: FaceRecognizer,
    private val swapper: FaceSwapper
) {
    fun process(source: Bitmap, target: Bitmap): Bitmap {
        val sourceFace = detector.detect(source).firstOrNull()
            ?: error("No face detected in source image")
        val targetFace = detector.detect(target).firstOrNull()
            ?: return target

        val sourceAligned = FaceAlignment.align(source, sourceFace.landmarks, 112)
        val targetAligned = FaceAlignment.align(target, targetFace.landmarks, 128)
        val embedding = recognizer.embedding(sourceAligned.bitmap)
        val swapped = swapper.swap(targetAligned.bitmap, embedding)
        return FaceAlignment.composite(target, swapped, targetAligned.inverse)
    }
}

/** Five-point ArcFace alignment using a least-squares affine transform. */
object FaceAlignment {
    private val reference112 = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f
    )

    data class Result(val bitmap: Bitmap, val inverse: Matrix)

    fun align(source: Bitmap, landmarks: FloatArray, size: Int): Result {
        val scale = size / 112f
        val dst = FloatArray(10) { i -> reference112[i] * scale }
        val forward = solveAffine(landmarks.copyOf(10), dst)
        val aligned = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(aligned).drawBitmap(source, forward, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val inverse = Matrix()
        check(forward.invert(inverse)) { "Unable to invert face alignment transform" }
        return Result(aligned, inverse)
    }

    fun composite(target: Bitmap, swapped: Bitmap, inverse: Matrix): Bitmap {
        val output = target.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val softened = feathered(swapped)
        canvas.drawBitmap(softened, inverse, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun feathered(input: Bitmap): Bitmap {
        val out = input.copy(Bitmap.Config.ARGB_8888, true)
        val cx = (out.width - 1) / 2f
        val cy = (out.height - 1) / 2f
        val rx = out.width * 0.49f
        val ry = out.height * 0.49f
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val d = dx * dx + dy * dy
                val alpha = when {
                    d <= 0.62f -> 255
                    d >= 0.98f -> 0
                    else -> ((0.98f - d) / 0.36f * 255f).toInt().coerceIn(0, 255)
                }
                val p = out.getPixel(x, y)
                out.setPixel(x, y, Color.argb(alpha, Color.red(p), Color.green(p), Color.blue(p)))
            }
        }
        return out
    }

    /** Least-squares affine map from five source points to five destination points. */
    private fun solveAffine(src: FloatArray, dst: FloatArray): Matrix {
        var sxx = 0.0; var sxy = 0.0; var sx = 0.0
        var syy = 0.0; var sy = 0.0; val n = 5.0
        var bx1 = 0.0; var bx2 = 0.0; var bx3 = 0.0
        var by1 = 0.0; var by2 = 0.0; var by3 = 0.0
        for (i in 0 until 5) {
            val x = src[i * 2].toDouble(); val y = src[i * 2 + 1].toDouble()
            val u = dst[i * 2].toDouble(); val v = dst[i * 2 + 1].toDouble()
            sxx += x * x; sxy += x * y; sx += x
            syy += y * y; sy += y
            bx1 += x * u; bx2 += y * u; bx3 += u
            by1 += x * v; by2 += y * v; by3 += v
        }
        val a = arrayOf(
            doubleArrayOf(sxx, sxy, sx),
            doubleArrayOf(sxy, syy, sy),
            doubleArrayOf(sx, sy, n)
        )
        val ux = solve3(a, doubleArrayOf(bx1, bx2, bx3))
        val vy = solve3(a, doubleArrayOf(by1, by2, by3))
        return Matrix().apply {
            setValues(floatArrayOf(
                ux[0].toFloat(), ux[1].toFloat(), ux[2].toFloat(),
                vy[0].toFloat(), vy[1].toFloat(), vy[2].toFloat(),
                0f, 0f, 1f
            ))
        }
    }

    private fun solve3(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val a = Array(3) { i -> doubleArrayOf(matrix[i][0], matrix[i][1], matrix[i][2], vector[i]) }
        for (col in 0 until 3) {
            var pivot = col
            for (row in col + 1 until 3) if (kotlin.math.abs(a[row][col]) > kotlin.math.abs(a[pivot][col])) pivot = row
            require(kotlin.math.abs(a[pivot][col]) > 1e-9) { "Degenerate face landmarks" }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val div = a[col][col]
            for (j in col..3) a[col][j] /= div
            for (row in 0 until 3) if (row != col) {
                val factor = a[row][col]
                for (j in col..3) a[row][j] -= factor * a[col][j]
            }
        }
        return doubleArrayOf(a[0][3], a[1][3], a[2][3])
    }
}
