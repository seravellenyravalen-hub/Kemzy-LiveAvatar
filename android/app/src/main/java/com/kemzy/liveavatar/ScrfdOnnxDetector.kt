package com.kemzy.liveavatar

import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * SCRFD 10G/KPS adapter matching the InsightFace output layout:
 * score_8/16/32, bbox_8/16/32, kps_8/16/32.
 *
 * The decoder follows the public InsightFace SCRFD implementation: 2 anchors
 * per feature-map location, distances scaled by stride, five keypoints and NMS.
 */
class ScrfdOnnxDetector(
    modelBytes: ByteArray,
    private val threshold: Float = 0.5f,
    private val nmsThreshold: Float = 0.4f,
    private val inputSize: Int = 640
) : FaceDetector, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelBytes, OrtSession.SessionOptions())
    private val inputName = session.inputNames.first()

    override fun detect(frame: Bitmap): List<DetectedFace> {
        val scale = min(inputSize.toFloat() / frame.width, inputSize.toFloat() / frame.height)
        val resizedWidth = max(1, (frame.width * scale).toInt())
        val resizedHeight = max(1, (frame.height * scale).toInt())
        val resized = Bitmap.createScaledBitmap(frame, resizedWidth, resizedHeight, true)
        val input = FloatArray(1 * 3 * inputSize * inputSize)
        var offset = 0
        for (channel in 0..2) {
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val pixel = if (x < resizedWidth && y < resizedHeight) resized.getPixel(x, y) else Color.BLACK
                    val value = when (channel) {
                        0 -> Color.red(pixel)
                        1 -> Color.green(pixel)
                        else -> Color.blue(pixel)
                    }
                    input[offset++] = value / 128f - 127.5f / 128f
                }
            }
        }

        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val byName = resultNamesToValues(result)
                val candidates = ArrayList<Candidate>()
                for (stride in intArrayOf(8, 16, 32)) {
                    val scores = floatTensor(byName["score_$stride"] ?: error("Missing score_$stride"))
                    val boxes = floatTensor(byName["bbox_$stride"] ?: error("Missing bbox_$stride"))
                    val kps = floatTensor(byName["kps_$stride"] ?: error("Missing kps_$stride"))
                    decodeStride(stride, scores, boxes, kps, scale, candidates)
                }
                return nms(candidates)
                    .sortedByDescending { it.score }
                    .map { DetectedFace(it.box, it.landmarks) }
            }
        }
    }

    private fun resultNamesToValues(result: OrtSession.Result): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (i in 0 until result.size()) out[session.outputNames.elementAt(i)] = result[i]?.value
        return out
    }

    private fun floatTensor(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> flatten(value)
        else -> error("SCRFD output must be float tensor, got ${value?.javaClass?.name}")
    }

    private fun flatten(value: Array<*>): FloatArray {
        val out = ArrayList<Float>()
        fun visit(v: Any?) {
            when (v) {
                is FloatArray -> v.forEach(out::add)
                is Array<*> -> v.forEach(::visit)
                null -> Unit
                else -> error("Unexpected SCRFD tensor element: ${v.javaClass.name}")
            }
        }
        visit(value)
        return out.toFloatArray()
    }

    private fun decodeStride(
        stride: Int,
        scores: FloatArray,
        boxes: FloatArray,
        keypoints: FloatArray,
        scale: Float,
        output: MutableList<Candidate>
    ) {
        val grid = inputSize / stride
        val count = grid * grid * 2
        require(scores.size >= count && boxes.size >= count * 4 && keypoints.size >= count * 10) {
            "SCRFD $stride output has unexpected size"
        }
        var index = 0
        for (y in 0 until grid) {
            for (x in 0 until grid) {
                for (anchor in 0 until 2) {
                    val score = scores[index]
                    if (score >= threshold) {
                        val cx = x * stride.toFloat()
                        val cy = y * stride.toFloat()
                        val b = index * 4
                        val box = floatArrayOf(
                            (cx - boxes[b] * stride) / scale,
                            (cy - boxes[b + 1] * stride) / scale,
                            (cx + boxes[b + 2] * stride) / scale,
                            (cy + boxes[b + 3] * stride) / scale
                        )
                        val k = index * 10
                        val landmarks = FloatArray(10)
                        for (j in 0 until 5) {
                            landmarks[j * 2] = (cx + keypoints[k + j * 2] * stride) / scale
                            landmarks[j * 2 + 1] = (cy + keypoints[k + j * 2 + 1] * stride) / scale
                        }
                        output += Candidate(score, box, landmarks)
                    }
                    index++
                }
            }
        }
    }

    private fun nms(candidates: List<Candidate>): List<Candidate> {
        val remaining = candidates.sortedByDescending { it.score }.toMutableList()
        val kept = ArrayList<Candidate>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { iou(best.box, it.box) > nmsThreshold }
        }
        return kept
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val left = max(a[0], b[0]); val top = max(a[1], b[1])
        val right = min(a[2], b[2]); val bottom = min(a[3], b[3])
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val areaA = max(0f, a[2] - a[0]) * max(0f, a[3] - a[1])
        val areaB = max(0f, b[2] - b[0]) * max(0f, b[3] - b[1])
        return intersection / (areaA + areaB - intersection).coerceAtLeast(1e-6f)
    }

    override fun close() = session.close()

    private data class Candidate(val score: Float, val box: FloatArray, val landmarks: FloatArray)
}
