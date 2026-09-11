package com.kemzy.liveavatar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Records the frames produced by the avatar engine instead of recording the raw camera.
 * MediaRecorder's input Surface gives us H.264 video plus AAC microphone audio without
 * introducing another third-party media stack.
 */
class AvatarSurfaceRecorder(private val context: Context) {
    companion object {
        private const val WIDTH = 720
        private const val HEIGHT = 1280
        private const val FPS = 30
        private const val VIDEO_BITRATE = 4_000_000
        private const val AUDIO_BITRATE = 128_000
        private const val AUDIO_SAMPLE_RATE = 44_100
    }

    private var recorder: MediaRecorder? = null
    private var inputSurface: android.view.Surface? = null
    private var tempFile: File? = null
    private var recording = false

    val isRecording: Boolean get() = recording

    @Synchronized
    fun start(): Boolean {
        if (recording) return true
        return try {
            val file = File.createTempFile("kemzy-avatar-", ".mp4", context.cacheDir)
            val mediaRecorder = MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.setVideoEncodingBitRate(VIDEO_BITRATE)
            mediaRecorder.setVideoFrameRate(FPS)
            mediaRecorder.setVideoSize(WIDTH, HEIGHT)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncodingBitRate(AUDIO_BITRATE)
            mediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.prepare()
            mediaRecorder.start()
            tempFile = file
            inputSurface = mediaRecorder.surface
            recorder = mediaRecorder
            recording = true
            true
        } catch (_: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            inputSurface = null
            tempFile?.delete()
            tempFile = null
            recording = false
            false
        }
    }

    @Synchronized
    fun drawFrame(bitmap: Bitmap) {
        if (!recording) return
        val surface = inputSurface ?: return
        val canvas: Canvas = try {
            surface.lockCanvas(null)
        } catch (_: Exception) {
            return
        }
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            val scale = minOf(
                WIDTH.toFloat() / bitmap.width.toFloat(),
                HEIGHT.toFloat() / bitmap.height.toFloat()
            )
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (WIDTH - drawWidth) / 2f
            val top = (HEIGHT - drawHeight) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), null)
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
        }
    }

    @Synchronized
    fun stop(): Uri? {
        if (!recording) return null
        recording = false
        val activeRecorder = recorder
        recorder = null
        inputSurface = null
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.reset() }
        runCatching { activeRecorder?.release() }

        val source = tempFile ?: return null
        tempFile = null
        if (!source.exists() || source.length() == 0L) {
            source.delete()
            return null
        }
        return publish(source)
    }

    @Synchronized
    fun release() {
        if (recording) stop() else {
            runCatching { recorder?.release() }
            recorder = null
            inputSurface = null
            tempFile?.delete()
            tempFile = null
        }
    }

    private fun publish(source: File): Uri? {
        val name = "Kemzy-LiveAvatar-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis()) + ".mp4"
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Kemzy-LiveAvatar")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    put(MediaStore.Video.Media.DATA, File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "Kemzy-LiveAvatar/$name"
                    ).absolutePath)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
            source.delete()
            uri
        } catch (_: Exception) {
            source.delete()
            null
        }
    }
}
