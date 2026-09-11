package com.kemzy.liveavatar

import java.security.MessageDigest

/**
 * App-private authentication policy. The passcode is never stored in plaintext.
 * Biometric is deliberately optional and can never be enabled without a valid
 * passcode confirmation first.
 */
object PrivacyGate {
    private const val PASSCODE_SHA256 = "b539a1f69bbf2eeba6363e2fc1b41dfe7a39080379104622bb55286e4a4f5a7c"

    fun verifyPasscode(candidate: CharSequence): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(candidate.toString().toByteArray(Charsets.UTF_8))
        val actual = digest.joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.US_ASCII),
            PASSCODE_SHA256.toByteArray(Charsets.US_ASCII)
        )
    }
}
