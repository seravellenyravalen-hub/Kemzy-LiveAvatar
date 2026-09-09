package com.kemzy.liveavatar

import android.content.Context
import android.net.Uri
import java.io.File

class VoiceAssetStore(private val context: Context) {
    private val directory = File(context.filesDir, "voice_assets").apply { mkdirs() }

    fun newRecordingFile(): File = File(directory, "voice_${System.currentTimeMillis()}.m4a")

    fun import(uri: Uri): File {
        val destination = File(directory, "import_${System.currentTimeMillis()}.audio")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read selected audio" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }
}
