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
}
