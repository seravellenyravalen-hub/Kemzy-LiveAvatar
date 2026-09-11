package com.kemzy.liveavatar

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** Loads the user-selected source image without copying it into the repository. */
class SourceFaceBitmapLoader(
    private val resolver: ContentResolver
) {
    fun load(uri: Uri): Bitmap {
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open source face image." }
            return BitmapFactory.decodeStream(input)
                ?: error("Selected source image could not be decoded.")
        }
    }
}
