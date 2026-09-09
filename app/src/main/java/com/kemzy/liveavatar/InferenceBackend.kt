package com.kemzy.liveavatar

enum class InferenceBackend {
    CPU,
    XNNPACK,
    NNAPI
}

object InferenceBackendSelector {
    fun candidates(apiLevel: Int, nnapiAvailable: Boolean): List<InferenceBackend> {
        if (apiLevel < 27) return listOf(InferenceBackend.CPU)
        return buildList {
            add(InferenceBackend.XNNPACK)
            add(InferenceBackend.CPU)
            if (nnapiAvailable) add(InferenceBackend.NNAPI)
        }
    }

    fun select(apiLevel: Int, nnapiAvailable: Boolean): InferenceBackend =
        candidates(apiLevel, nnapiAvailable).first()
}
