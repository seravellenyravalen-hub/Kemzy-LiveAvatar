package com.kemzy.liveavatar

import android.content.Context
import android.os.Build

/**
 * Describes whether this Android build can host a platform virtual camera.
 *
 * This is deliberately a capability probe, not a fake registration API. A normal APK
 * must never claim that another app can see Kemzy unless the platform exposes the
 * system virtual-camera service.
 */
class VirtualCameraCapability(
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val virtualDeviceManagerPresent: Boolean,
    val virtualCameraSupported: Boolean
) {
    enum class Status { AVAILABLE, UNAVAILABLE }

    val isSupported: Boolean
        get() = apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            virtualDeviceManagerPresent && virtualCameraSupported

    val status: Status
        get() = if (isSupported) Status.AVAILABLE else Status.UNAVAILABLE

    companion object {
        fun probe(context: Context): VirtualCameraCapability {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false)
            }

            val manager = context.getSystemService("virtualdevice")
                ?: return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false)

            val supported = runCatching {
                val method = manager.javaClass.getMethod("isVirtualCameraSupported")
                (method.invoke(null) as? Boolean) ?: false
            }.getOrDefault(false)

            return VirtualCameraCapability(Build.VERSION.SDK_INT, true, supported)
        }
    }
}
