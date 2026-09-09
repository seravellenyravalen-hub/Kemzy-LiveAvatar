package com.kemzy.liveavatar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Runtime capability probe for using Kemzy as an Android-wide camera source.
 *
 * Presence of VirtualDeviceManager is not enough to prove that an application
 * can publish a virtual camera. OEM configuration and the platform API level
 * determine whether that path is actually available.
 */
object CameraCapability {
    enum class OutputRoute {
        NATIVE_VIRTUAL_CAMERA,
        EXTERNAL_CAMERA,
        BACKGROUND_PROCESSING,
        SCREEN_SHARE_ONLY,
        UNAVAILABLE
    }

    data class Report(
        val apiLevel: Int,
        val virtualDeviceManagerAvailable: Boolean,
        val virtualCameraApiPresent: Boolean,
        val virtualCameraSupported: Boolean?,
        val externalCameraPresent: Boolean,
        val overlayPermissionGranted: Boolean,
        val cameraPermissionGranted: Boolean,
        val backgroundCameraServicePossible: Boolean
    ) {
        fun bestRoute(): OutputRoute = when {
            virtualCameraSupported == true -> OutputRoute.NATIVE_VIRTUAL_CAMERA
            externalCameraPresent -> OutputRoute.EXTERNAL_CAMERA
            backgroundCameraServicePossible -> OutputRoute.BACKGROUND_PROCESSING
            overlayPermissionGranted -> OutputRoute.SCREEN_SHARE_ONLY
            else -> OutputRoute.UNAVAILABLE
        }
    }

    fun inspect(context: Context): Report {
        val vdm = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(android.companion.virtual.VirtualDeviceManager::class.java)
        } else null

        val (apiPresent, apiSupported) = probeVirtualCameraApi(vdm)
        val externalCamera = hasExternalCamera(context)
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        return Report(
            apiLevel = Build.VERSION.SDK_INT,
            virtualDeviceManagerAvailable = vdm != null,
            virtualCameraApiPresent = apiPresent,
            virtualCameraSupported = apiSupported,
            externalCameraPresent = externalCamera,
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            cameraPermissionGranted = cameraGranted,
            backgroundCameraServicePossible = Build.VERSION.SDK_INT >= 34 && cameraGranted
        )
    }

    private fun probeVirtualCameraApi(vdm: Any?): Pair<Boolean, Boolean?> {
        if (vdm == null) return false to null
        val method: Method = vdm.javaClass.methods.firstOrNull {
            it.name == "isVirtualCameraSupported" &&
                it.parameterTypes.isEmpty() &&
                Modifier.isPublic(it.modifiers)
        } ?: return false to null

        return true to runCatching { method.invoke(vdm) as? Boolean }.getOrNull()
    }

    private fun hasExternalCamera(context: Context): Boolean {
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        return runCatching {
            manager.cameraIdList.any { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_EXTERNAL
            }
        }.getOrDefault(false)
    }
}
