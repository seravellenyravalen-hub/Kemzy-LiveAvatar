package com.kemzy.liveavatar

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

/**
 * Android-side face-swap processor boundary.
 *
 * The processor is deliberately fail-closed: it never paints a fake result.
 * A concrete model adapter must provide the detector/recognizer preprocessing,
 * 128x128 alignment, 512-D identity latent generation, INSwapper inference,
 * and inverse-affine compositing.
 */
interface FaceDetector {
    fun detect(frame: Bitmap): List<DetectedFace>
}

data class DetectedFace(
    val box: FloatArray,
    val landmarks: FloatArray
)

interface FaceRecognizer {
    fun embedding(face: Bitmap): FloatArray
}

interface FaceSwapper {
    fun swap(alignedTarget: Bitmap, sourceEmbedding: FloatArray): Bitmap
}

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

        val sourceCrop = cropFace(source, sourceFace)
        val targetCrop = cropFace(target, targetFace)
        val embedding = recognizer.embedding(sourceCrop)
        val swapped = swapper.swap(targetCrop, embedding)
        return pasteFace(target, swapped, targetFace)
    }

    private fun cropFace(bitmap: Bitmap, face: DetectedFace): Bitmap {
        val x = face.box[0].coerceIn(0f, (bitmap.width - 1).toFloat()).toInt()
        val y = face.box[1].coerceIn(0f, (bitmap.height - 1).toFloat()).toInt()
        val right = face.box[2].coerceIn((x + 1).toFloat(), bitmap.width.toFloat()).toInt()
        val bottom = face.box[3].coerceIn((y + 1).toFloat(), bitmap.height.toFloat()).toInt()
        return Bitmap.createBitmap(bitmap, x, y, right - x, bottom - y)
    }

    private fun pasteFace(target: Bitmap, swapped: Bitmap, face: DetectedFace): Bitmap {
        val output = target.copy(target.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(output)
        val src = android.graphics.Rect(0, 0, swapped.width, swapped.height)
        val dst = android.graphics.Rect(
            face.box[0].toInt(), face.box[1].toInt(),
            face.box[2].toInt(), face.box[3].toInt()
        )
        canvas.drawBitmap(swapped, src, dst, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
        return output
    }
}
