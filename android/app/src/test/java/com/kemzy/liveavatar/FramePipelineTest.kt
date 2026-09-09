package com.kemzy.liveavatar

import android.graphics.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals

class FramePipelineTest {
    @Test
    fun latestFrameWinsWhenConsumerFallsBehind() {
        val pipeline = FramePipeline(capacity = 1)
        pipeline.offer(Frame(1, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)))
        pipeline.offer(Frame(2, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)))
        assertEquals(2, pipeline.poll()?.id)
    }
}
