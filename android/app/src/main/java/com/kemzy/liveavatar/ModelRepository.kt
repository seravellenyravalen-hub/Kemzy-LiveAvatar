package com.kemzy.liveavatar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File

class ModelRepository(private val context: Context) {
    private val modelDir by lazy { File(context.filesDir, "models").apply { mkdirs() } }

    fun installedModels(): List<File> = modelDir.listFiles()?.filter { it.extension == "onnx" }.orEmpty()

    fun model(name: String): File? = File(modelDir, name).takeIf { it.isFile && it.length() > 0 }

    fun emap(): File? = File(modelDir, "inswapper_emap.bin").takeIf { it.isFile && it.length() == 512L * 512L * 4L }

    fun liaModel(): File? = File(modelDir, "generator.onnx").takeIf { it.isFile && it.length() > 0 }

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

    fun importEmap(resolver: ContentResolver, uri: Uri): File {
        val destination = File(modelDir, "inswapper_emap.bin")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open EMAP file." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        require(destination.length() == 512L * 512L * 4L) {
            "EMAP must contain exactly 262144 float32 values (1,048,576 bytes)."
        }
        return destination
    }

    fun importLiaGenerator(resolver: ContentResolver, uri: Uri): File {
        val destination = File(modelDir, "generator.onnx")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open LIA generator." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        require(destination.length() > 0L) { "LIA generator is empty." }
        return destination
    }

    fun importLiaPart(resolver: ContentResolver, uri: Uri, part: Int): File {
        require(part in 0..3) { "LIA generator part must be 0..3." }
        val destination = File(modelDir, "generator.onnx.part$part")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open LIA generator part $part." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        require(destination.length() > 0L) { "LIA generator part $part is empty." }
        return destination
    }

    fun hasAllLiaParts(): Boolean = (0..3).all { File(modelDir, "generator.onnx.part$it").isFile }

    /** Concatenates parts with buffered streams; never builds a second 192 MB byte array in Java heap. */
    fun assembleLiaParts(): File {
        require(hasAllLiaParts()) { "All four LIA generator parts are required." }
        val destination = File(modelDir, "generator.onnx")
        destination.outputStream().buffered().use { output ->
            (0..3).forEach { part ->
                File(modelDir, "generator.onnx.part$part").inputStream().buffered().use { input ->
                    input.copyTo(output, bufferSize = 1024 * 1024)
                }
            }
        }
        require(destination.length() > 0L) { "Assembled LIA generator is empty." }
        return destination
    }
}
