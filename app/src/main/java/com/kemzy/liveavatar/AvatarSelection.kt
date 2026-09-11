package com.kemzy.liveavatar

class AvatarSelection(initialUri: String? = null) {
    var uri: String? = initialUri
        private set

    fun select(uri: String) {
        this.uri = uri
    }

    fun clear() {
        uri = null
    }
}
