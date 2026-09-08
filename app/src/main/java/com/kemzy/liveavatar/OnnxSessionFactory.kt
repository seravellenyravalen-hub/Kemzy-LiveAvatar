package com.kemzy.liveavatar

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

class OnnxSessionFactory(
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
) {
    fun create(modelPath: String, backend: InferenceBackend): OrtSession {
        val options = OrtSession.SessionOptions()
        try {
            when (backend) {
                InferenceBackend.NNAPI -> options.addNnapi()
                InferenceBackend.XNNPACK -> options.addXnnpack(emptyMap())
                InferenceBackend.CPU -> options.addCPU(true)
            }
            return environment.createSession(modelPath, options)
        } finally {
            options.close()
        }
    }
}
