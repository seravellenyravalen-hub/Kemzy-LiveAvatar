package com.kemzy.liveavatar

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies a picked reference into app-private storage so a live session never depends
 * on a removable/content-provider URI or network-backed document.
 */
class ReferenceImageStore(private val context: Context) {
    private val directory: File
        get() = File(context.filesDir, "avatar-references").apply { mkdirs() }

    fun persist(uri: Uri): String {
        val extension = when (uri.toString().substringAfterLast('.', "").lowercase()) {
            "png" -> "png"
            "webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "reference-${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Reference image could not be opened" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        require(destination.isFile && destination.length() > 0L) { "Reference image could not be stored" }
        return Uri.fromFile(destination).toString()
    }

    fun delete(uri: String?) {
        if (uri.isNullOrBlank()) return
        runCatching {
            val file = File(Uri.parse(uri).path ?: return)
            if (file.parentFile?.canonicalFile == directory.canonicalFile) file.delete()
        }
    }
}
