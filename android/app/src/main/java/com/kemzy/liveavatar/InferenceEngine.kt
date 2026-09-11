package com.kemzy.liveavatar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/** File-backed ONNX inference engine. The model is passed to ORT by path rather than
 * copied into a Java ByteArray, avoiding a large Java-heap allocation on Android. */
class OnnxInferenceEngine private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val label: String
) : Closeable {
    companion object {
        fun fromFile(file: File, label: String): OnnxInferenceEngine {
            require(file.isFile && file.length() > 0L) { "$label model is missing or empty" }
            val environment = OrtEnvironment.getEnvironment()
            return try {
                val options = OrtSession.SessionOptions()
                val session = environment.createSession(file.absolutePath, options)
                OnnxInferenceEngine(environment, session, label)
            } catch (error: Throwable) {
                environment.close()
                throw IllegalStateException("Unable to load $label model: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }
    }

    fun run(inputs: Map<String, OnnxTensor>): OrtSession.Result = session.run(inputs)

    fun inputNames(): Set<String> = session.inputNames

    fun outputNames(): Set<String> = session.outputNames

    override fun close() {
        session.close()
        environment.close()
    }
}
