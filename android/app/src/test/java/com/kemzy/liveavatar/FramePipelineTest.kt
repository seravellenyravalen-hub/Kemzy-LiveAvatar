package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals

class FramePipelineTest {
    @Test
    fun latestFrameWinsWhenConsumerFallsBehind() {
        val pipeline = FramePipeline<Int>(capacity = 1)
        pipeline.offer(1)
        pipeline.offer(2)
        assertEquals(2, pipeline.poll())
    }
}
