package com.kemzy.liveavatar

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Imports a complete ONNX model selection and validates every installed graph. */
class LivePortraitModelImporter(
    private val repository: ModelRepository,
    private val resolver: ContentResolver
) {
    data class Result(
        val imported: List<String>,
        val ignored: List<String>,
        val missing: List<String>,
        val invalid: Map<String, String>
    ) {
        val ready: Boolean get() = missing.isEmpty() && invalid.isEmpty()
    }

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
        val validation = if (bundle.isComplete) LivePortraitModelValidator(repository).validate() else null
        val invalid = validation?.models?.filterNot { it.valid }?.associate { it.name to (it.error ?: "invalid graph") } ?: emptyMap()
        return Result(imported, ignored, bundle.missingRoles(), invalid)
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
