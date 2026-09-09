package com.kemzy.liveavatar

/** Session lock. The real passcode must be supplied by the app owner at configuration time. */
class PrivacyLock {
    private var unlocked = false

    fun isLocked(): Boolean = !unlocked

    fun unlock(candidate: String): Boolean {
        // Deliberately keep credential material out of source control.
        val configured = BuildConfig.PRIVACY_PASSCODE
        if (candidate == configured) unlocked = true
        return unlocked
    }

    fun lock() {
        unlocked = false
    }
}
