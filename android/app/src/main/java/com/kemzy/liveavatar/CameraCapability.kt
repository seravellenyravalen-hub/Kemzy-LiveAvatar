package com.kemzy.liveavatar

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Reports the Android capabilities that affect external/background video output.
 * It never assumes that a device exposes a virtual camera just because it is on
 * Android 15+; OEM/system configuration still controls availability.
 */
object CameraCapability {
    data class Report(
        val apiLevel: Int,
        val virtualDeviceManagerAvailable: Boolean,
        val overlayPermissionGranted: Boolean,
        val cameraPermissionGranted: Boolean,
        val backgroundCameraServicePossible: Boolean
    )

    fun inspect(context: Context): Report {
        val vdmAvailable = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(android.companion.virtual.VirtualDeviceManager::class.java) != null
        } else false

        val cameraGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return Report(
            apiLevel = Build.VERSION.SDK_INT,
            virtualDeviceManagerAvailable = vdmAvailable,
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            cameraPermissionGranted = cameraGranted,
            backgroundCameraServicePossible = Build.VERSION.SDK_INT >= 34 && cameraGranted
        )
    }
}
