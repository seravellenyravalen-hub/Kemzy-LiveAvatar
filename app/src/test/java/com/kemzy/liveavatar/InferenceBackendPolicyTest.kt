package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceBackendPolicyTest {
    @Test
    fun old_android_uses_cpu() {
        assertEquals(listOf(InferenceBackend.CPU), InferenceBackendSelector.candidates(26, true))
    }

    @Test
    fun modern_android_with_nnapi_prefers_xnnpack_then_cpu_then_nnapi() {
        assertEquals(
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU, InferenceBackend.NNAPI),
            InferenceBackendSelector.candidates(31, true)
        )
    }

    @Test
    fun modern_android_without_nnapi_prefers_xnnpack_then_cpu() {
        assertEquals(
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU),
            InferenceBackendSelector.candidates(31, false)
        )
    }
}
