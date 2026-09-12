package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePortraitAnimatorTest {
    @Test
    fun lia_contract_uses_256_square_inputs_and_20_value_start_motion() {
        val contract = LiaModelContract()
        assertEquals(256, contract.inputWidth)
        assertEquals(256, contract.inputHeight)
        assertEquals(20, contract.startMotionSize)
        assertEquals("in_src", contract.sourceInput)
        assertEquals("in_drv", contract.driverInput)
        assertEquals("in_drv_start_motion", contract.startMotionInput)
        assertEquals("in_power", contract.powerInput)
        assertEquals("out_drv_motion", contract.motionOutput)
        assertEquals("out", contract.imageOutput)
    }

    @Test
    fun lia_model_is_loaded_from_a_file_path_not_java_byte_array() {
        val constructor = OnnxInferenceEngine::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.firstOrNull() == java.io.File::class.java
        }
        assertTrue("LIA must use disk-backed ONNX loading", constructor != null)
    }
}
