package com.kemzy.liveavatar

import android.os.SystemClock

/**
 * Rate-limits passcode failures without persisting the secret or failure history.
 * A successful unlock resets the counter.
 */
class PrivacySecurityPolicy(
    private val maxAttempts: Int = 5,
    private val lockoutMillis: Long = 30_000L,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var failedAttempts = 0
    private var lockedUntil = 0L

    fun canAttempt(): Boolean = clock() >= lockedUntil

    fun recordFailure(): Long {
        if (!canAttempt()) return lockedUntil - clock()
        failedAttempts += 1
        if (failedAttempts >= maxAttempts) {
            lockedUntil = clock() + lockoutMillis
            failedAttempts = 0
            return lockoutMillis
        }
        return 0L
    }

    fun recordSuccess() {
        failedAttempts = 0
        lockedUntil = 0L
    }

    fun remainingLockoutMillis(): Long = maxOf(0L, lockedUntil - clock())
}
