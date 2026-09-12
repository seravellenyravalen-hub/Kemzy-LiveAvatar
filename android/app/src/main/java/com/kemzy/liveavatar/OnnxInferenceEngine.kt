package com.kemzy.liveavatar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/** File-backed ONNX Runtime session. Large models are never copied into a Java byte array. */
class OnnxInferenceEngine private constructor(
    private val session: OrtSession,
    private val label: String
) : Closeable {
    companion object {
        private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

        fun fromFile(file: File, label: String): OnnxInferenceEngine {
            require(file.isFile && file.length() > 0L) { "$label model is missing or empty" }
            return try {
                val options = OrtSession.SessionOptions()
                OnnxInferenceEngine(environment.createSession(file.absolutePath, options), label)
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Unable to load $label model: ${error.message ?: error.javaClass.simpleName}",
                    error
                )
            }
        }
    }

    fun run(inputs: Map<String, OnnxTensor>): OrtSession.Result = session.run(inputs)
    fun inputNames(): Set<String> = session.inputNames
    fun outputNames(): Set<String> = session.outputNames
    fun environment(): OrtEnvironment = environment

    override fun close() = session.close()
    override fun toString(): String = "OnnxInferenceEngine($label)"
}
