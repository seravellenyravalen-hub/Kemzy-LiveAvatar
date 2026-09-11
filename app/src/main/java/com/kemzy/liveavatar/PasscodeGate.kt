package com.kemzy.liveavatar

import java.security.MessageDigest

/** Privacy gate for the Kémzy UI. The PIN is never stored as plaintext. */
class PasscodeGate(
    private val expectedSha256: String = listOf(
        "b539a1f6", "9bbf2eeb", "a6363e2f", "c1b41dfe",
        "7a390803", "79104622", "bb55286e", "4a4f5a7c"
    ).joinToString("")
) {
    fun verify(passcode: String): Boolean {
        if (passcode.length != 6 || !passcode.all(Char::isDigit)) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(passcode.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(digest, hexToBytes(expectedSha256))
    }

    private fun hexToBytes(value: String): ByteArray {
        require(value.length == 64)
        return ByteArray(32) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
