package com.kemzy.liveavatar

import android.graphics.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals

class FramePipelineTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun latestFrameWinsWhenConsumerFallsBehind() {
        val pipeline = FramePipeline(capacity = 1)
        val first = null as Bitmap
        val second = null as Bitmap
        pipeline.offer(Frame(1, first))
        pipeline.offer(Frame(2, second))
        assertEquals(2, pipeline.poll()?.id)
    }
}
