package com.kemzy.liveavatar

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

class LocalVoiceController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var lastOutputPath: String? = null

    val isRecording: Boolean
        get() = recorder != null

    fun startRecording(output: File) {
        stopRecording()
        lastOutputPath = output.absolutePath
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(output.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): File? {
        val active = recorder ?: return null
        recorder = null
        return try {
            active.stop()
            active.release()
            lastOutputPath?.let(::File)
        } catch (_: Exception) {
            active.release()
            null
        }
    }

    fun startRecordingAndRemember(output: File) = startRecording(output)

    fun play(file: File, onComplete: () -> Unit = {}) {
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                it.release()
                player = null
                onComplete()
            }
            setOnErrorListener { mp, _, _ ->
                mp.release()
                player = null
                onComplete()
                true
            }
            prepare()
            start()
        }
    }

    fun stopPlayback() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    fun close() {
        stopRecording()
        stopPlayback()
    }
}
