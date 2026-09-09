package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class SourceFaceRepository(context: Context) {
    private val sourceFile = File(context.filesDir, "source_face.jpg")

    fun hasSource(): Boolean = sourceFile.isFile && sourceFile.length() > 0

    fun setSource(bitmap: Bitmap) {
        sourceFile.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)) { "Unable to persist source image" }
        }
    }

    fun loadSource(): Bitmap? = if (hasSource()) BitmapFactory.decodeFile(sourceFile.absolutePath) else null

    fun clear() {
        sourceFile.delete()
    }
}
