package com.kemzy.liveavatar

import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build

/** Capability-aware boundary for the system virtual-camera integration. */
interface VirtualCameraBridge : AutoCloseable {
    val isAvailable: Boolean
    fun publishFrame(frame: Bitmap): Boolean
    fun disconnect()
    override fun close() = disconnect()
}

class StockAndroidVirtualCameraBridge(context: Context) : VirtualCameraBridge {
    override val isAvailable: Boolean = detectSupport(context)

    override fun publishFrame(frame: Bitmap): Boolean = false
    override fun disconnect() = Unit

    private fun detectSupport(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        if (context.getSystemService(VirtualDeviceManager::class.java) == null) return false
        return runCatching {
            val method = VirtualDeviceManager::class.java.getMethod("isVirtualCameraSupported")
            method.invoke(null) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
