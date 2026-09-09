package com.kemzy.liveavatar

import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable

/** Real ONNX Runtime boundary used by the Android frame pipeline. */
interface InferenceEngine : Closeable {
    fun modelName(): String
    fun run(inputs: Map<String, OnnxValue>): Map<String, OnnxValue>
}

class OnnxInferenceEngine(
    private val modelBytes: ByteArray
) : InferenceEngine {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelBytes, OrtSession.SessionOptions())

    override fun modelName(): String = "ONNX Runtime model"

    override fun run(inputs: Map<String, OnnxValue>): Map<String, OnnxValue> {
        session.run(inputs).use { results ->
            val output = linkedMapOf<String, OnnxValue>()
            for (i in 0 until results.size()) {
                val value = results[i]?.value
                if (value is OnnxValue) output[session.outputNames.elementAt(i)] = value
            }
            return output
        }
    }

    override fun close() = session.close()
}
