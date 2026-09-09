package com.kemzy.liveavatar

import android.content.Context
import android.os.Build
import android.companion.virtual.VirtualDeviceManager

/** Capability-aware boundary for the system virtual-camera integration. */
interface VirtualCameraBridge : AutoCloseable {
    val isAvailable: Boolean
    fun publishFrame(frame: android.graphics.Bitmap): Boolean
    fun disconnect()
    override fun close() = disconnect()
}

class StockAndroidVirtualCameraBridge(context: Context) : VirtualCameraBridge {
    override val isAvailable: Boolean = detectSupport(context)

    override fun publishFrame(frame: android.graphics.Bitmap): Boolean = false
    override fun disconnect() = Unit

    private fun detectSupport(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val manager = context.getSystemService(VirtualDeviceManager::class.java) ?: return false
        return runCatching {
            val method = VirtualDeviceManager::class.java.getMethod("isVirtualCameraSupported")
            method.invoke(null) as? Boolean ?: false
        }.getOrElse { manager.virtualDevices.isNotEmpty() && false }
    }
}
