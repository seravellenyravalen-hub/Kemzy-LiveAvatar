package com.kemzy.liveavatar.output

import android.content.Context
import android.graphics.Bitmap
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.video.BufferVideoSource
import com.pedro.library.rtmp.RtmpStream
import java.util.concurrent.atomic.AtomicBoolean

class RtmpLiveOutput(
    context: Context,
    private val endpoint: String,
    private val onMessage: (String) -> Unit = {}
) : LiveOutput {
    override val name: String = "RTMP"
    private val running = AtomicBoolean(false)
    private val videoSource = BufferVideoSource(BufferVideoSource.Format.RGB, 1_500_000)
    private val stream = RtmpStream(context, checker, videoSource, MicrophoneSource())

    private val checker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) = onMessage("RTMP connecting")
        override fun onConnectionSuccess() = onMessage("RTMP connected")
        override fun onConnectionFailed(reason: String) = onMessage("RTMP failed: $reason")
        override fun onDisconnect() = onMessage("RTMP disconnected")
        override fun onAuthError() = onMessage("RTMP authentication failed")
        override fun onAuthSuccess() = onMessage("RTMP authentication accepted")
        override fun onNewBitrate(bitrate: Long) = Unit
    }

    init { require(endpoint.isNotBlank()) { "RTMP endpoint is required" } }

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        check(stream.prepareVideo(512, 512, 1_500_000, 15, 2, 0)) { "Unable to prepare RTMP video encoder" }
        check(stream.prepareAudio(44_100, false, 64_000)) { "Unable to prepare RTMP audio encoder" }
        stream.startStream(endpoint)
    }

    override fun submit(frame: Bitmap) {
        if (!running.get()) return
        val scaled = if (frame.width == 512 && frame.height == 512) frame else Bitmap.createScaledBitmap(frame, 512, 512, true)
        val pixels = IntArray(512 * 512)
        scaled.getPixels(pixels, 0, 512, 0, 0, 512, 512)
        videoSource.setBuffer(pixels)
        if (scaled !== frame) scaled.recycle()
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { stream.stopStream() }
    }
}
