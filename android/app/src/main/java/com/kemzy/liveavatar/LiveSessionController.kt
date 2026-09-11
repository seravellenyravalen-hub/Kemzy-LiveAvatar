package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.VideoSource
import com.pedro.library.rtmp.RtmpStream
import kotlin.math.max

class LiveSessionController(
    private val state: LiveSessionState
) {
    val isRunning: Boolean get() = state.isLive
    fun start() = state.startLive()
    fun stop() = state.stopLive()
}

/** Owns exactly one processed bitmap and recycles it when replaced or cleared. */
class BitmapOwnershipSlot {
    private val lock = Any()
    private var bitmap: Bitmap? = null

    fun replace(next: Bitmap) {
        synchronized(lock) {
            val previous = bitmap
            bitmap = next
            if (previous != null && previous !== next && !previous.isRecycled) previous.recycle()
        }
    }

    fun withBitmap(block: (Bitmap) -> Unit) {
        synchronized(lock) { bitmap?.takeUnless { it.isRecycled }?.let(block) }
    }

    fun clear() {
        synchronized(lock) {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
            bitmap = null
        }
    }
}

/** VideoSource that publishes the latest processed Kemzy frame, never the raw camera. */
class ProcessedBitmapSource : VideoSource() {
    private val frames = BitmapOwnershipSlot()
    private var surface: Surface? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun submit(frame: Bitmap) = frames.replace(frame)

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean =
        width > 0 && height > 0 && fps > 0

    override fun start(surfaceTexture: SurfaceTexture) {
        if (running) return
        surfaceTexture.setDefaultBufferSize(width, height)
        surface = Surface(surfaceTexture)
        running = true
        worker = Thread({
            val intervalMs = max(1L, 1000L / fps)
            while (running) {
                val started = System.currentTimeMillis()
                frames.withBitmap { bitmap ->
                    try {
                        val canvas = surface?.lockCanvas(null)
                        if (canvas != null) {
                            canvas.drawColor(android.graphics.Color.BLACK)
                            canvas.drawBitmap(bitmap, null, canvas.clipBounds, paint)
                            surface?.unlockCanvasAndPost(canvas)
                        }
                    } catch (_: Throwable) { }
                }
                val elapsed = System.currentTimeMillis() - started
                try {
                    Thread.sleep(max(1L, intervalMs - elapsed))
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "kemzy-rtmp-video")
        worker?.start()
    }

    override fun stop() {
        running = false
        worker?.interrupt()
        runCatching { worker?.join(500) }
        worker = null
        surface?.release()
        surface = null
    }

    override fun release() {
        stop()
        frames.clear()
    }

    override fun isRunning(): Boolean = running
}

/** Publishes actual processed frames to an RTMP/MediaMTX endpoint for official MYCAM Live RTMP. */
class RtmpLiveOutput(
    context: Context,
    private val onStatus: (String) -> Unit = {}
) : ConnectChecker, AutoCloseable {
    private val videoSource = ProcessedBitmapSource()
    private val stream = RtmpStream(context, this, videoSource, NoAudioSource())

    @Volatile var isStreaming: Boolean = false
        private set

    fun submit(frame: Bitmap) = videoSource.submit(frame)

    fun start(endpoint: String, width: Int = 720, height: Int = 1280, fps: Int = 15) {
        require(endpoint.startsWith("rtmp://") || endpoint.startsWith("rtmps://")) {
            "RTMP endpoint must start with rtmp:// or rtmps://"
        }
        check(!isStreaming) { "RTMP output is already running." }
        check(stream.prepareVideo(width, height, 2_000_000, fps)) {
            "Unable to prepare the device video encoder."
        }
        check(stream.prepareAudio(44_100, false, 64_000)) {
            "Unable to prepare the RTMP audio track."
        }
        isStreaming = true
        stream.startStream(endpoint)
        onStatus("RTMP connecting")
    }

    fun stop() {
        if (!isStreaming) {
            videoSource.release()
            return
        }
        isStreaming = false
        runCatching { stream.stopStream() }
        videoSource.release()
        onStatus("RTMP stopped")
    }

    override fun close() {
        stop()
        runCatching { stream.release() }
    }

    override fun onConnectionStarted(url: String) = onStatus("RTMP connecting")
    override fun onConnectionSuccess() = onStatus("RTMP connected")
    override fun onConnectionFailed(reason: String) {
        isStreaming = false
        videoSource.release()
        onStatus("RTMP failed: $reason")
    }
    override fun onDisconnect() {
        isStreaming = false
        videoSource.release()
        onStatus("RTMP disconnected")
    }
    override fun onAuthError() {
        isStreaming = false
        videoSource.release()
        onStatus("RTMP authentication failed")
    }
    override fun onAuthSuccess() = onStatus("RTMP authentication accepted")
    override fun onNewBitrate(bitrate: Long) = onStatus("RTMP ${bitrate / 1000} kbps")
}
