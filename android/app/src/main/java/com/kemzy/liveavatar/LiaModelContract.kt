package com.kemzy.liveavatar

/** Exact input/output contract used by DeepFaceLive's LIA generator.onnx. */
class LiaModelContract {
    val inputWidth = 256
    val inputHeight = 256
    val startMotionSize = 20
    val sourceInput = "in_src"
    val driverInput = "in_drv"
    val startMotionInput = "in_drv_start_motion"
    val powerInput = "in_power"
    val motionOutput = "out_drv_motion"
    val imageOutput = "out"
}
