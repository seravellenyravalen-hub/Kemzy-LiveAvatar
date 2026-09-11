package com.kemzy.liveavatar

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes a local audio file to PCM16 without putting encoded audio on the live bus. */
class ImportedAudioPcmSource(private val file: File) : VoicePcmSource {
    override var sampleRateHz: Int = 48_000
        private set
    override var channelCount: Int = 1
        private set

    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var inputDone = false
    private var outputDone = false
    private var pending = ShortArray(0)
    private var pendingIndex = 0

    override fun start(): Boolean {
        stop()
        if (!file.isFile) return false
        return runCatching {
            val ex = MediaExtractor()
            ex.setDataSource(file.absolutePath)
            var track = -1
            for (i in 0 until ex.trackCount) {
                val format = ex.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    break
                }
            }
            if (track < 0) {
                ex.release()
                return false
            }
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("No audio MIME")
            sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val dc = MediaCodec.createDecoderByType(mime)
            dc.configure(format, null, null, 0)
            dc.start()
            extractor = ex
            decoder = dc
            inputDone = false
            outputDone = false
            pending = ShortArray(0)
            pendingIndex = 0
            true
        }.getOrElse {
            stop()
            false
        }
    }

    override fun read(target: ShortArray, offset: Int, length: Int): Int {
        if (decoder == null || outputDone || length == 0) return 0
        var written = copyPending(target, offset, length)
        if (written > 0) return written

        val info = MediaCodec.BufferInfo()
        while (written == 0 && !outputDone) {
            val dc = decoder ?: return written
            if (!inputDone) {
                val inputIndex = dc.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = dc.getInputBuffer(inputIndex) ?: return written
                    input.clear()
                    val ex = extractor ?: return written
                    val size = ex.readSampleData(input, 0)
                    if (size < 0) {
                        dc.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        dc.queueInputBuffer(inputIndex, 0, size, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }

            val outputIndex = dc.dequeueOutputBuffer(info, 10_000)
            when {
                outputIndex >= 0 -> {
                    val output = dc.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val bytes = ByteArray(output.remaining())
                        output.get(bytes)
                        val shorts = ShortArray(bytes.size / 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        pending = shorts
                        pendingIndex = 0
                        written = copyPending(target, offset, length)
                    }
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    dc.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
            }
        }
        return written
    }

    private fun copyPending(target: ShortArray, offset: Int, length: Int): Int {
        val available = pending.size - pendingIndex
        if (available <= 0) return 0
        val count = minOf(length, available)
        System.arraycopy(pending, pendingIndex, target, offset, count)
        pendingIndex += count
        if (pendingIndex >= pending.size) {
            pending = ShortArray(0)
            pendingIndex = 0
        }
        return count
    }

    override fun stop() {
        decoder?.let { runCatching { it.stop() }; it.release() }
        extractor?.release()
        decoder = null
        extractor = null
        inputDone = false
        outputDone = false
        pending = ShortArray(0)
        pendingIndex = 0
    }
}
