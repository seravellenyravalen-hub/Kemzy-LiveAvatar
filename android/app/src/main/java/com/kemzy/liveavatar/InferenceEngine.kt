package com.kemzy.liveavatar

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable

/** Real ONNX Runtime boundary used by the Android frame pipeline. */
interface InferenceEngine : Closeable {
    fun modelName(): String
    fun run(inputs: Map<String, Any>): Map<String, Any>
}

class OnnxInferenceEngine(
    private val modelBytes: ByteArray,
    private val preferredProviders: List<String> = listOf("CPUExecutionProvider")
) : InferenceEngine {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = createSession()

    private fun createSession(): OrtSession {
        val options = OrtSession.SessionOptions()
        // Android provider availability differs by device/build. CPU is the
        // deterministic fallback; unsupported accelerators are never faked.
        return environment.createSession(modelBytes, options)
    }

    override fun modelName(): String = "ONNX Runtime model"

    override fun run(inputs: Map<String, Any>): Map<String, Any> {
        val feed = inputs.mapValues { (_, value) -> value }
        session.run(feed).use { results ->
            val output = linkedMapOf<String, Any>()
            for (i in 0 until results.size()) {
                val value = results[i].value
                if (value != null) output[session.outputNames.elementAt(i)] = value
            }
            return output
        }
    }

    override fun close() {
        session.close()
    }
}
