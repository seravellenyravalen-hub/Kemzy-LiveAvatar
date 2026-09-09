package com.kemzy.liveavatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures Kemzy's microphone input and applies the selected voice effect.
 * The processed PCM remains inside Kemzy unless an explicitly supported audio
 * output route is available. It is not an attempt to inject audio into a
 * third-party call application.
 */
class VoiceCaptureController(private val context: Context) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val effect = VoiceEffectProcessor()
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    fun setMode(mode: VoiceEffectProcessor.Mode) {
        effect.mode = mode
    }

    fun start(onProcessedAudio: (ShortArray) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) return
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required"
        }
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        recorder = record
        worker = Thread {
            val buffer = ShortArray(minBuffer / 2)
            try {
                record.startRecording()
                while (running.get()) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count > 0) onProcessedAudio(effect.process(buffer.copyOf(count)))
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                recorder = null
            }
        }.apply {
            name = "KemzyVoiceEffect"
            start()
        }
    }

    override fun close() {
        running.set(false)
        worker?.interrupt()
        worker = null
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
    }
}
