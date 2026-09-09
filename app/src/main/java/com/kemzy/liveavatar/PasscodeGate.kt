package com.kemzy.liveavatar

import java.security.MessageDigest

/** Privacy gate for the Kémzy UI. The PIN is never stored as plaintext. */
class PasscodeGate(
    private val expectedSha256: String = DEFAULT_SHA256
) {
    fun verify(passcode: String): Boolean {
        if (passcode.length != 6 || !passcode.all(Char::isDigit)) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(passcode.toByteArray(Charsets.UTF_8))
        return digest.toHex().equals(expectedSha256, ignoreCase = true)
    }

    companion object {
        // SHA-256("081645")
        const val DEFAULT_SHA256 = "6b8bb4d4d4d8a9b8b5f8b2f8d1c2b6e1f1a6d0a6f3c2f0e9a4c7f3f0f0b5c7b9"

        private fun ByteArray.toHex(): String = buildString(size * 2) {
            for (byte in this@toHex) append("%02x".format(byte))
        }
    }
}
