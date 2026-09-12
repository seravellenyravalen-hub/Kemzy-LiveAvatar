package com.kemzy.liveavatar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OnnxInferenceEngineTest {
    @Test
    fun exposes_a_disk_backed_model_file_constructor() {
        val hasFileConstructor = OnnxInferenceEngine::class.java.declaredConstructors.any { constructor ->
            constructor.parameterTypes.firstOrNull() == File::class.java
        }
        assertTrue("ONNX inference must support loading models directly from disk", hasFileConstructor)
    }
}
