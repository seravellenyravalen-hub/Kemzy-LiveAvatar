package com.kemzy.liveavatar

import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySecurityPolicyTest {
    @Test
    fun locksAfterFiveFailures() {
        var now = 0L
        val policy = PrivacySecurityPolicy(clock = { now })
        repeat(4) { assertTrue(policy.recordFailure() == 0L) }
        assertTrue(policy.canAttempt())
        assertTrue(policy.recordFailure() > 0L)
        assertTrue(!policy.canAttempt())
        now += 30_000L
        assertTrue(policy.canAttempt())
    }

    @Test
    fun successResetsFailureState() {
        val policy = PrivacySecurityPolicy()
        repeat(4) { policy.recordFailure() }
        policy.recordSuccess()
        assertTrue(policy.canAttempt())
        assertTrue(policy.recordFailure() == 0L)
    }
}
