package com.kemzy.liveavatar.output

import android.graphics.Bitmap

interface VirtualCameraBridge : AutoCloseable {
    val isAvailable: Boolean
    val state: OutputState
    fun connect(): Result<Unit>
    fun submit(frame: Bitmap): Result<Unit>
    fun disconnect()
    override fun close() = disconnect()
}

/**
 * Kémzy intentionally does not pretend to be a system camera. MyCam owns the
 * privileged/device-specific virtual-camera handoff; this bridge is the clean
 * producer boundary that Kémzy can connect to when MyCam exposes one.
 */
class MyCamBridge : VirtualCameraBridge {
    override val isAvailable: Boolean = false
    override var state: OutputState = OutputState.STOPPED
        private set

    override fun connect(): Result<Unit> {
        state = OutputState.ERROR
        return Result.failure(IllegalStateException("MyCam bridge is not connected on this device"))
    }

    override fun submit(frame: Bitmap): Result<Unit> =
        Result.failure(IllegalStateException("MyCam bridge is unavailable"))

    override fun disconnect() {
        state = OutputState.STOPPED
    }
}
