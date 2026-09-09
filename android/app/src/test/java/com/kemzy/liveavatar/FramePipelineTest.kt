package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals

class FramePipelineTest {
    @Test
    fun latestFrameWinsWhenConsumerFallsBehind() {
        val pipeline = FramePipeline(capacity = 1)
        pipeline.offer(Frame(1))
        pipeline.offer(Frame(2))
        assertEquals(2, pipeline.poll()?.id)
    }
}
