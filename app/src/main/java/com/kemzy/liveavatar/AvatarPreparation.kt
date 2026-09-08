package com.kemzy.liveavatar

data class AvatarPreparationResult(
    val isReady: Boolean,
    val reason: String? = null
) {
    companion object {
        val ready = AvatarPreparationResult(true)

        fun invalid(reason: String) = AvatarPreparationResult(false, reason)
    }
}

class AvatarPreparation {
    fun validate(uri: String?): AvatarPreparationResult {
        if (uri.isNullOrBlank()) return AvatarPreparationResult.invalid("Avatar URI is empty")
        return AvatarPreparationResult.ready
    }
}
