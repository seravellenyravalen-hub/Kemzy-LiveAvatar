package com.kemzy.liveavatar

import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency PCM voice-effect stage for audio owned by Kemzy.
 * It does not hook or replace another app's protected call-audio stream.
 */
class VoiceEffectProcessor {
    enum class Mode { NATURAL, DEEP, BRIGHT, ROBOT }

    @Volatile
    var mode: Mode = Mode.NATURAL

    fun process(input: ShortArray): ShortArray {
        if (input.isEmpty() || mode == Mode.NATURAL) return input.copyOf()
        val ratio = when (mode) {
            Mode.DEEP -> 0.82f
            Mode.BRIGHT -> 1.18f
            Mode.ROBOT, Mode.NATURAL -> 1.0f
        }
        val out = ShortArray(input.size)
        for (i in out.indices) {
            val sourcePosition = i / ratio
            val left = sourcePosition.toInt().coerceIn(0, input.lastIndex)
            val right = min(left + 1, input.lastIndex)
            val fraction = sourcePosition - left
            var sample = input[left] + (input[right] - input[left]) * fraction
            if (mode == Mode.ROBOT) {
                sample = if (i % 2 == 0) sample * 0.65f else sample * -0.65f
            }
            out[i] = max(-32768f, min(32767f, sample)).toInt().toShort()
        }
        return out
    }
}
