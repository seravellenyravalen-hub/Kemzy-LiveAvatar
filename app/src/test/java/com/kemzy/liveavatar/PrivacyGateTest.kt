package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyGateTest {
    @Test
    fun acceptsRequiredPasscode() {
        assertTrue(PrivacyGate.verifyPasscode("081645"))
    }

    @Test
    fun rejectsWrongPasscode() {
        assertFalse(PrivacyGate.verifyPasscode("081646"))
        assertFalse(PrivacyGate.verifyPasscode(""))
    }
}
