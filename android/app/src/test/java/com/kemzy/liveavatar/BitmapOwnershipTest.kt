package com.kemzy.liveavatar

import android.graphics.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapOwnershipTest {
    @Test
    fun replacingOwnedBitmapRecyclesPreviousFrame() {
        val slot = BitmapOwnershipSlot()
        val first = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        slot.replace(first)
        slot.replace(second)

        assertTrue(first.isRecycled)
        assertTrue(!second.isRecycled)

        slot.clear()
        assertTrue(second.isRecycled)
    }
}
