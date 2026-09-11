package com.kemzy.liveavatar

import android.graphics.Bitmap
import java.io.File

/**
 * Owns the real detector/recognizer/swapper chain once compatible user-supplied
 * ONNX weights have been installed. Model loading is lazy so the app can still
 * open when models are absent.
 */
class OnDeviceSwapRuntime(private val bundle: ModelBundle) : AutoCloseable {
    private var detector: ScrfdOnnxDetector? = null
    private var recognizer: ArcFaceOnnxRecognizer? = null
    private var swapper: InSwapperOnnx? = null
    private var processor: DeepLiveCamFrameProcessor? = null

    fun isReady(): Boolean = bundle.isComplete

    fun process(source: Bitmap, target: Bitmap): Bitmap {
        check(bundle.isComplete) { "Required Deep-Live-Cam models are not installed" }
        val pipeline = processor ?: buildPipeline().also { processor = it }
        return pipeline.process(source, target)
    }

    private fun buildPipeline(): DeepLiveCamFrameProcessor {
        val detectorFile = requireFile(bundle.detector, "det_10g.onnx")
        val recognizerFile = requireFile(bundle.recognizer, "w600k_r50.onnx")
        val swapperFile = requireFile(bundle.swapper, "inswapper_128.onnx")

        // IMPORTANT: keep model weights on disk. ONNX Runtime opens these files
        // directly, avoiding the large Java-heap ByteArrays that previously made
        // Live startup fail with a ~128 MB heap limit on low-memory phones.
        detector = ScrfdOnnxDetector(detectorFile)
        recognizer = ArcFaceOnnxRecognizer(recognizerFile)
        swapper = InSwapperOnnx(swapperFile)
        return DeepLiveCamFrameProcessor(detector!!, recognizer!!, swapper!!)
    }

    override fun close() {
        swapper?.close()
        recognizer?.close()
        detector?.close()
        swapper = null
        recognizer = null
        detector = null
        processor = null
    }

    private fun requireFile(file: File?, expected: String): File =
        file?.takeIf { it.isFile && it.length() > 0 }
            ?: error("Required model missing: $expected")
}
