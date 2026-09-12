package com.kemzy.liveavatar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * Safe inference boundary for Android. Automatically allocated outputs are
 * copied out of ONNX Runtime's native result scope before that scope closes.
 */
interface InferenceEngine : Closeable {
    fun modelName(): String
    fun inputNames(): Set<String>
    fun outputNames(): Set<String>
    fun run(inputs: Map<String, OnnxTensor>): Map<String, Any?>
}

class OnnxInferenceEngine(
    private val modelFile: File,
    private val name: String = "ONNX Runtime model"
) : InferenceEngine {
    init {
        require(modelFile.isFile && modelFile.length() > 0L) {
            "ONNX model file is missing or empty: ${modelFile.absolutePath}"
        }
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions()
    )

    override fun modelName(): String = name

    override fun inputNames(): Set<String> = session.inputNames

    override fun outputNames(): Set<String> = session.outputNames

    @Throws(OrtException::class)
    override fun run(inputs: Map<String, OnnxTensor>): Map<String, Any?> {
        session.run(inputs).use { results ->
            val output = LinkedHashMap<String, Any?>(results.size())
            for (i in 0 until results.size()) {
                val value = results[i]
                output[session.outputNames.elementAt(i)] = value?.value
            }
            return output
        }
    }

    override fun close() {
        session.close()
    }
}
