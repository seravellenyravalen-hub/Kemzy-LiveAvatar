package com.kemzy.liveavatar

import java.security.MessageDigest

/** Session PIN lock. Only the SHA-256 digest of the PIN is stored. */
class PrivacyLock(
    private val verifier: (String) -> Boolean = { candidate ->
        sha256(candidate) == BuildConfig.PRIVACY_PIN_SHA256
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

    companion object {
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
