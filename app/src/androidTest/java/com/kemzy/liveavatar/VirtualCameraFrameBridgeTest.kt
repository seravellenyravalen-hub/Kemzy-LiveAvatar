package com.kemzy.liveavatar

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualCameraFrameBridgeTest {
    @Test
    fun snapshotKeepsLatestFrameAvailableForOtherConsumers() {
        val frame = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            StreamingFrameBus.publish(frame)
            val snapshot = StreamingFrameBus.snapshot()
            assertNotSame(frame, snapshot)
            assertEquals(8, snapshot?.width)
            assertEquals(8, snapshot?.height)
            assertEquals(8, StreamingFrameBus.snapshot()?.width)
            snapshot?.recycle()
        } finally {
            frame.recycle()
            StreamingFrameBus.clear()
        }
    }
}
