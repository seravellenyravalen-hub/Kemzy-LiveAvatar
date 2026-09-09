package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

/**
 * Android-native equivalent of the alignment/paste-back step used by
 * Deep-Live-Cam/InsightFace: map the live face to the ArcFace canonical
 * coordinate system, run INSwapper, then map the result back to the original
 * camera frame. This preserves the selected source identity while the target
 * face supplies pose/expression.
 */
object FaceAlignment {
    private val arcFace112 = arrayOf(
        PointF(38.2946f, 51.6963f),
        PointF(73.5318f, 51.5014f),
        PointF(56.0252f, 71.7366f),
        PointF(41.5493f, 92.3655f),
        PointF(70.7299f, 92.2041f)
    )

    fun canonical112(): Array<PointF> = arcFace112.map { PointF(it.x, it.y) }.toTypedArray()

    fun canonical(size: Int): Array<PointF> {
        val scale = size / 112f
        return arcFace112.map { PointF(it.x * scale, it.y * scale) }.toTypedArray()
    }

    /** Uses the three most stable landmarks (eyes + nose) for an affine map. */
    fun affineToCanonical(points: Array<PointF>, size: Int): Matrix? {
        if (points.size < 3 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val src = floatArrayOf(
            points[0].x, points[0].y,
            points[1].x, points[1].y,
            points[2].x, points[2].y
        )
        val dstCanonical = canonical(size)
        val dst = floatArrayOf(
            dstCanonical[0].x, dstCanonical[0].y,
            dstCanonical[1].x, dstCanonical[1].y,
            dstCanonical[2].x, dstCanonical[2].y
        )
        return Matrix().also { matrix ->
            if (!matrix.setPolyToPoly(src, 0, dst, 0, 3)) return null
        }
    }

    fun align(bitmap: Bitmap, landmarks: Array<PointF>, size: Int): Bitmap? {
        val matrix = affineToCanonical(landmarks, size) ?: return null
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    /** Pastes an aligned swap back into the original frame using the inverse map. */
    fun pasteBack(
        output: Bitmap,
        alignedSwap: Bitmap,
        sourceToAligned: Matrix,
        opacity: Float = 1f
    ) {
        val inverse = Matrix()
        if (!sourceToAligned.invert(inverse)) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        }
        Canvas(output).drawBitmap(alignedSwap, inverse, paint)
    }

    fun featheredMask(size: Int): Bitmap {
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 255 }
        canvas.drawOval(size * .06f, size * .04f, size * .94f, size * .98f, paint)
        return mask
    }
}
