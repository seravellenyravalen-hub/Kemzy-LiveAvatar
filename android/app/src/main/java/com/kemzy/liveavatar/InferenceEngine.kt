package com.kemzy.liveavatar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * Safe inference boundary for Android. Models can be opened directly from disk
 * so large ONNX files are not first copied into the Java heap.
 */
interface InferenceEngine : Closeable {
    fun modelName(): String
    fun inputNames(): Set<String>
    fun outputNames(): Set<String>
    fun run(inputs: Map<String, OnnxTensor>): Map<String, Any?>
}

class OnnxInferenceEngine private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val name: String
) : InferenceEngine {
    companion object {
        fun fromFile(file: File, name: String = "ONNX Runtime model"): OnnxInferenceEngine {
            require(file.isFile && file.length() > 0L) {
                "$name model is missing or empty"
            }
            val environment = OrtEnvironment.getEnvironment()
            return try {
                val session = environment.createSession(
                    file.absolutePath,
                    OrtSession.SessionOptions()
                )
                OnnxInferenceEngine(environment, session, name)
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Unable to load $name model: ${error.message ?: error.javaClass.simpleName}",
                    error
                )
            }
        }
    }

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
