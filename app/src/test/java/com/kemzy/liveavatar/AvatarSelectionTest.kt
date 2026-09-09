package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarSelectionTest {
    @Test
    fun initialSelectionIsEmpty() {
        val selection = AvatarSelection()
        assertNull(selection.uri)
    }

    @Test
    fun selectingUriStoresIt() {
        val selection = AvatarSelection()
        selection.select("content://media/external/images/1")
        assertEquals("content://media/external/images/1", selection.uri)
    }

    @Test
    fun clearingSelectionRemovesIt() {
        val selection = AvatarSelection("content://media/external/images/1")
        selection.clear()
        assertNull(selection.uri)
    }
}
