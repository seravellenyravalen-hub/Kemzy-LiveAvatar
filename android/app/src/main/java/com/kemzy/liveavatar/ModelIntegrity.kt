package com.kemzy.liveavatar

import java.io.File
import java.security.MessageDigest

object ModelIntegrity {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun canonicalGeneratorMatches(file: File): Boolean =
        file.isFile && sha256(file) == LivePortraitModelSpec.warpingSpadeSha256
}
