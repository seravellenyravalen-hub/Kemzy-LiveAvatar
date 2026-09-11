package com.kemzy.liveavatar

enum class InferenceBackend {
    CPU,
    XNNPACK,
    NNAPI
}

object InferenceBackendSelector {
    fun select(apiLevel: Int, nnapiAvailable: Boolean): InferenceBackend {
        if (apiLevel < 27) return InferenceBackend.CPU
        return if (nnapiAvailable) InferenceBackend.NNAPI else InferenceBackend.XNNPACK
    }
}
