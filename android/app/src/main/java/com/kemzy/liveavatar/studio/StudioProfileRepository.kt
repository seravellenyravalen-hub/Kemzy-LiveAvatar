package com.kemzy.liveavatar.studio

import android.content.Context

class StudioProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("kemzy_studio", Context.MODE_PRIVATE)

    fun load(): StudioProfile = runCatching {
        StudioProfile(
            sourceUri = prefs.getString(KEY_SOURCE, null),
            voiceMode = enumOrDefault(prefs.getString(KEY_VOICE, null), StudioProfile.VoiceMode.NORMAL),
            outputMode = enumOrDefault(prefs.getString(KEY_OUTPUT, null), StudioProfile.OutputMode.PREVIEW),
            rtmpEndpoint = prefs.getString(KEY_RTMP, "") ?: "",
            frontCamera = prefs.getBoolean(KEY_FRONT, true),
            quality = enumOrDefault(prefs.getString(KEY_QUALITY, null), StudioProfile.Quality.BALANCED)
        )
    }.getOrDefault(StudioProfile())

    fun save(profile: StudioProfile) {
        prefs.edit()
            .putString(KEY_SOURCE, profile.sourceUri)
            .putString(KEY_VOICE, profile.voiceMode.name)
            .putString(KEY_OUTPUT, profile.outputMode.name)
            .putString(KEY_RTMP, profile.rtmpEndpoint)
            .putBoolean(KEY_FRONT, profile.frontCamera)
            .putString(KEY_QUALITY, profile.quality.name)
            .apply()
    }

    fun updateSource(uri: String?) = save(load().copy(sourceUri = uri))
    fun updateVoice(mode: StudioProfile.VoiceMode) = save(load().copy(voiceMode = mode))
    fun updateOutput(mode: StudioProfile.OutputMode, endpoint: String = load().rtmpEndpoint) =
        save(load().copy(outputMode = mode, rtmpEndpoint = endpoint))

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_SOURCE = "source_uri"
        const val KEY_VOICE = "voice_mode"
        const val KEY_OUTPUT = "output_mode"
        const val KEY_RTMP = "rtmp_endpoint"
        const val KEY_FRONT = "front_camera"
        const val KEY_QUALITY = "quality"
    }
}
