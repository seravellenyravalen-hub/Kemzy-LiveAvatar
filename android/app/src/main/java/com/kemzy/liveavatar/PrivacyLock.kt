package com.kemzy.liveavatar

/** Session lock. Credential material is supplied by configuration, never hardcoded here. */
class PrivacyLock(
    private val verifier: (String) -> Boolean = { candidate ->
        candidate == BuildConfig.PRIVACY_PASSCODE
    }
) {
    private var unlocked = false

    fun isLocked(): Boolean = !unlocked

    fun unlock(candidate: String): Boolean {
        if (verifier(candidate)) unlocked = true
        return unlocked
    }

    fun lock() {
        unlocked = false
    }
}
