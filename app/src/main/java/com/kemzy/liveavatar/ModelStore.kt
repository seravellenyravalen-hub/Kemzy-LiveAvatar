package com.kemzy.liveavatar

import android.content.Context
import java.io.File

class ModelStore(context: Context) {
    private val root = File(context.filesDir, "face-models").apply { mkdirs() }

    fun fileFor(model: FaceModelDescriptor): File = File(root, model.fileName)

    fun isPresent(model: FaceModelDescriptor): Boolean {
        val file = fileFor(model)
        return file.isFile && file.length() > 0L
    }

    fun missingRequiredModels(): List<FaceModelDescriptor> =
        FaceModelManifest.required.filterNot(::isPresent)
}
