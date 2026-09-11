package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceBackendPolicyTest {
    @Test
    fun old_android_uses_cpu() {
        assertEquals(InferenceBackend.CPU, InferenceBackendSelector.select(26, true))
    }

    @Test
    fun modern_android_prefers_nnapi_when_available() {
        assertEquals(InferenceBackend.NNAPI, InferenceBackendSelector.select(31, true))
    }

    @Test
    fun modern_android_falls_back_to_xnnpack_without_nnapi() {
        assertEquals(InferenceBackend.XNNPACK, InferenceBackendSelector.select(31, false))
    }
}
