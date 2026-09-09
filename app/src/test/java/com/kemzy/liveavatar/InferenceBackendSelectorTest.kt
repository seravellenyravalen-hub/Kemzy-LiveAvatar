package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceBackendSelectorTest {
    @Test
    fun androidWithNnapiAvailable_prefersXnnpackThenCpuBeforeNnapi() {
        assertEquals(
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU, InferenceBackend.NNAPI),
            InferenceBackendSelector.candidates(apiLevel = 35, nnapiAvailable = true)
        )
    }

    @Test
    fun androidWithoutNnapi_usesXnnpackThenCpu() {
        assertEquals(
            listOf(InferenceBackend.XNNPACK, InferenceBackend.CPU),
            InferenceBackendSelector.candidates(apiLevel = 35, nnapiAvailable = false)
        )
    }

    @Test
    fun oldAndroid_usesCpuOnly() {
        assertEquals(
            listOf(InferenceBackend.CPU),
            InferenceBackendSelector.candidates(apiLevel = 26, nnapiAvailable = true)
        )
    }
}
