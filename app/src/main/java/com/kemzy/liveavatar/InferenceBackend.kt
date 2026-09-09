package com.kemzy.liveavatar

enum class InferenceBackend {
    CPU,
    XNNPACK,
    NNAPI
}

object InferenceBackendSelector {
    fun candidates(apiLevel: Int, nnapiAvailable: Boolean): List<InferenceBackend> {
        if (apiLevel < 27) return listOf(InferenceBackend.CPU)
        return if (nnapiAvailable) {
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU, InferenceBackend.NNAPI)
        } else {
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU)
        }
    }

    fun select(apiLevel: Int, nnapiAvailable: Boolean): InferenceBackend =
        candidates(apiLevel, nnapiAvailable).first()
}
