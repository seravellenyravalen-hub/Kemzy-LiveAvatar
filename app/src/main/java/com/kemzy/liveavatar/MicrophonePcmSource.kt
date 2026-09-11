package com.kemzy.liveavatar

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Captures mono PCM16 for Kémzy's own processing/system bridge. */
class MicrophonePcmSource(
    override val sampleRateHz: Int = 48_000,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC
) : VoicePcmSource {
    override val channelCount: Int = 1
    private var recorder: AudioRecord? = null

    override fun start(): Boolean {
        stop()
        val min = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) return false
        val size = (min * 2).coerceAtLeast(sampleRateHz / 10 * 2)
        val created = AudioRecord(
            audioSource,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            size
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }
        return runCatching {
            created.startRecording()
            recorder = created
            true
        }.getOrElse {
            created.release()
            false
        }
    }

    override fun read(target: ShortArray, offset: Int, length: Int): Int =
        recorder?.read(target, offset, length, AudioRecord.READ_BLOCKING) ?: 0

    override fun stop() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
    }
}
