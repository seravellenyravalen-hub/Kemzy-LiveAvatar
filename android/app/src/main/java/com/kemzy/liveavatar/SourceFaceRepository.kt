package com.kemzy.liveavatar

/**
 * Holds the selected source-face URI for the live session.
 * Storage/import is intentionally separate so Android document permissions can
 * be handled by the Activity without coupling the live engine to UI code.
 */
class SourceFaceRepository {
    var currentSource: String? = null
        private set

    fun select(uri: String) {
        require(uri.isNotBlank()) { "Source face URI cannot be blank." }
        currentSource = uri
    }

    fun clear() {
        currentSource = null
    }
}
