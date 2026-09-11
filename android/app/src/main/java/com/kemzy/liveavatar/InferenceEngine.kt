package com.kemzy.liveavatar

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * Safe inference boundary for Android.
 *
 * Models are opened directly from their app-private .onnx file path. This is
 * intentional: reading the complete model into a ByteArray first duplicates
 * the model in the managed heap and can hit Android's heap growth limit before
 * ONNX Runtime gets a chance to map/use the model.
 */
interface InferenceEngine : Closeable {
    fun modelName(): String
    fun inputNames(): Set<String>
    fun outputNames(): Set<String>
    fun run(inputs: Map<String, OnnxTensor>): Map<String, Any?>
}

class OnnxInferenceEngine(
    private val modelFile: File,
    private val name: String = modelFile.name
) : InferenceEngine {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = createSession(modelFile)

    private fun createSession(file: File): OrtSession {
        require(file.isFile) { "ONNX model file does not exist: ${file.absolutePath}" }
        require(file.length() > 0L) { "ONNX model file is empty: ${file.absolutePath}" }

        return environment.createSession(
            file.absolutePath,
            OrtSession.SessionOptions()
        )
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
