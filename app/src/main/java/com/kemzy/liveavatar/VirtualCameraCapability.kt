package com.kemzy.liveavatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Describes whether this Android build can host a platform virtual camera and whether
 * the current Kemzy process has the privileged registration permission required by
 * Android's VirtualDevice stack.
 */
class VirtualCameraCapability(
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val virtualDeviceManagerPresent: Boolean,
    val virtualCameraSupported: Boolean,
    val createVirtualDevicePermissionGranted: Boolean
) {
    enum class Status { AVAILABLE, PRIVILEGE_REQUIRED, UNAVAILABLE }

    val platformSupported: Boolean
        get() = apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            virtualDeviceManagerPresent && virtualCameraSupported

    val isSupported: Boolean
        get() = platformSupported && createVirtualDevicePermissionGranted

    val status: Status
        get() = when {
            !platformSupported -> Status.UNAVAILABLE
            !createVirtualDevicePermissionGranted -> Status.PRIVILEGE_REQUIRED
            else -> Status.AVAILABLE
        }

    companion object {
        fun probe(context: Context): VirtualCameraCapability {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false, false)
            }

            val manager = context.getSystemService("virtualdevice")
                ?: return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false, false)

            val supported = runCatching {
                val method = manager.javaClass.getMethod("isVirtualCameraSupported")
                (method.invoke(null) as? Boolean) ?: false
            }.getOrDefault(false)

            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CREATE_VIRTUAL_DEVICE
            ) == PackageManager.PERMISSION_GRANTED

            return VirtualCameraCapability(
                Build.VERSION.SDK_INT,
                true,
                supported,
                permissionGranted
            )
        }
    }
}
