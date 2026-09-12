package com.kemzy.liveavatar.studio

/** Persisted configuration for one Kémzy Studio workspace. */
data class StudioProfile(
    val sourceUri: String? = null,
    val voiceMode: VoiceMode = VoiceMode.NORMAL,
    val outputMode: OutputMode = OutputMode.PREVIEW,
    val rtmpEndpoint: String = "",
    val frontCamera: Boolean = true,
    val quality: Quality = Quality.BALANCED
) {
    enum class VoiceMode { NORMAL, MALE, FEMALE, IMPORTED }
    enum class OutputMode { PREVIEW, RTMP, MYCAM }
    enum class Quality { PERFORMANCE, BALANCED, QUALITY }
}
