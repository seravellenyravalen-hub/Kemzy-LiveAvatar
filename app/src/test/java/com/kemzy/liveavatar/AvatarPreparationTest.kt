package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPreparationTest {
    @Test
    fun blank_uri_is_rejected() {
        val result = AvatarPreparationResult.invalid("Avatar URI is empty")
        assertFalse(result.isReady)
        assertEquals("Avatar URI is empty", result.reason)
    }

    @Test
    fun prepared_face_is_ready() {
        val result = AvatarPreparationResult.ready
        assertTrue(result.isReady)
    }
}
