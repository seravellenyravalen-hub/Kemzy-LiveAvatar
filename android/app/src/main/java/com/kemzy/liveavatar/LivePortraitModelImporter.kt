package com.kemzy.liveavatar

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Imports a complete ONNX model selection without requiring one picker action per model. */
class LivePortraitModelImporter(
    private val repository: ModelRepository,
    private val resolver: ContentResolver
) {
    data class Result(
        val imported: List<String>,
        val ignored: List<String>,
        val missing: List<String>
    )

    fun importUris(uris: List<Uri>): Result {
        val imported = mutableListOf<String>()
        val ignored = mutableListOf<String>()

        for (uri in uris.distinct()) {
            val displayName = displayName(uri) ?: uri.lastPathSegment.orEmpty()
            if (displayName !in LivePortraitModelSpec.requiredNames) {
                ignored += displayName
                continue
            }
            repository.importModel(resolver, uri, displayName)
            imported += displayName
        }

        val bundle = LiveModelBundleRepository(repository).inspect()
        return Result(
            imported = imported,
            ignored = ignored,
            missing = bundle.missingRoles()
        )
    }

    private fun displayName(uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }
}
