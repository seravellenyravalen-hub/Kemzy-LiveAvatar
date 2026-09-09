package com.kemzy.liveavatar

import android.companion.virtual.VirtualDeviceManager
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
        private const val CREATE_VIRTUAL_DEVICE_PERMISSION =
            "android.permission.CREATE_VIRTUAL_DEVICE"

        fun probe(context: Context): VirtualCameraCapability {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false, false)
            }

            val managerPresent = runCatching {
                context.getSystemService(VirtualDeviceManager::class.java) != null
            }.getOrDefault(false)

            if (!managerPresent) {
                return VirtualCameraCapability(Build.VERSION.SDK_INT, false, false, false)
            }

            val supported = runCatching {
                // isVirtualCameraSupported() is a static framework API. Invoke it on the
                // class, not on the service instance. Reflection keeps the app tolerant of
                // Android 15 OEM builds where the API may be absent/flagged.
                val method = VirtualDeviceManager::class.java
                    .getMethod("isVirtualCameraSupported")
                (method.invoke(null) as? Boolean) ?: false
            }.getOrDefault(false)

            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                CREATE_VIRTUAL_DEVICE_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED

            return fromPlatformProbe(
                apiLevel = Build.VERSION.SDK_INT,
                virtualDeviceManagerPresent = managerPresent,
                virtualCameraSupported = supported,
                createVirtualDevicePermissionGranted = permissionGranted
            )
        }

        internal fun fromPlatformProbe(
            apiLevel: Int,
            virtualDeviceManagerPresent: Boolean,
            virtualCameraSupported: Boolean,
            createVirtualDevicePermissionGranted: Boolean
        ): VirtualCameraCapability = VirtualCameraCapability(
            apiLevel = apiLevel,
            virtualDeviceManagerPresent = virtualDeviceManagerPresent,
            virtualCameraSupported = virtualCameraSupported,
            createVirtualDevicePermissionGranted = createVirtualDevicePermissionGranted
        )
    }
}
