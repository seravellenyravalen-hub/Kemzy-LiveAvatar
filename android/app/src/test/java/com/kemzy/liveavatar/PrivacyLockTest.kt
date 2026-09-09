package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyLockTest {
    @Test fun lock_is_required_after_session_end() {
        val lock = PrivacyLock { candidate -> candidate == "correct" }
        assertTrue(lock.isLocked())
        lock.unlock("correct")
        assertFalse(lock.isLocked())
        lock.lock()
        assertTrue(lock.isLocked())
    }

    @Test fun biometric_is_not_part_of_lock_contract() {
        val methods = PrivacyLock::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { it.contains("biometric", ignoreCase = true) })
    }

    @Test fun configured_pin_unlocks() {
        val lock = PrivacyLock()
        assertTrue(lock.unlock("08164590231"))
        assertFalse(lock.isLocked())
    }

    @Test fun wrong_pin_does_not_unlock() {
        val lock = PrivacyLock()
        assertFalse(lock.unlock("0816459023"))
        assertTrue(lock.isLocked())
    }
}
