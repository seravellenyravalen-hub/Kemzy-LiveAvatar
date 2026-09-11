package com.kemzy.liveavatar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File

class ModelRepository(private val context: Context) {
    private val modelDir by lazy { File(context.filesDir, "models").apply { mkdirs() } }

    fun installedModels(): List<File> = modelDir.listFiles()?.filter { it.extension == "onnx" }.orEmpty()

    fun model(name: String): File? = File(modelDir, name).takeIf { it.isFile && it.length() > 0 }

    fun importModel(source: File, name: String): File {
        require(name.endsWith(".onnx")) { "Only ONNX models are supported." }
        val destination = File(modelDir, name)
        source.copyTo(destination, overwrite = true)
        return destination
    }

    fun importModel(resolver: ContentResolver, uri: Uri, name: String): File {
        require(name.endsWith(".onnx")) { "Only ONNX models are supported." }
        val destination = File(modelDir, name)
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected model." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        require(destination.length() > 0L) { "Selected model is empty." }
        return destination
    }
}
