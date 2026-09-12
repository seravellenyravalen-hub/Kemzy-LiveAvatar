package com.kemzy.liveavatar.output

import android.graphics.Bitmap

interface LiveOutput : AutoCloseable {
    val name: String
    fun start() {}
    fun submit(frame: Bitmap)
    fun stop() {}
    override fun close() = stop()
}

enum class OutputState { STOPPED, STARTING, RUNNING, ERROR }
