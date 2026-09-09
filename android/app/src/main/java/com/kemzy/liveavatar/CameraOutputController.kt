package com.kemzy.liveavatar

import android.content.Context

/**
 * Selects the strongest output path that the current Android device actually
 * exposes. It never claims that a normal APK can force another application to
 * consume a camera device that Android has not registered.
 */
class CameraOutputController(private val context: Context) {
    data class Status(
        val route: CameraCapability.OutputRoute,
        val message: String,
        val consumerApps: String
    )

    fun inspect(): Status {
        val report = CameraCapability.inspect(context)
        return when (report.bestRoute()) {
            CameraCapability.OutputRoute.NATIVE_VIRTUAL_CAMERA -> Status(
                route = CameraCapability.OutputRoute.NATIVE_VIRTUAL_CAMERA,
                message = "Kemzy virtual-camera capability is exposed by this device. Registration/output can use the Android camera framework.",
                consumerApps = "Camera-framework apps that expose this device camera can potentially select it."
            )
            CameraCapability.OutputRoute.EXTERNAL_CAMERA -> Status(
                route = CameraCapability.OutputRoute.EXTERNAL_CAMERA,
                message = "An external camera device is visible to Android. Kemzy can use the external-camera route where the device/framework permits it.",
                consumerApps = "Camera-framework apps that support external cameras."
            )
            CameraCapability.OutputRoute.BACKGROUND_PROCESSING -> Status(
                route = CameraCapability.OutputRoute.BACKGROUND_PROCESSING,
                message = "Background camera processing is available, but Android has not exposed a native Kemzy virtual camera.",
                consumerApps = "Kemzy can continue processing in the background; this does not automatically replace another app's camera."
            )
            CameraCapability.OutputRoute.SCREEN_SHARE_ONLY -> Status(
                route = CameraCapability.OutputRoute.SCREEN_SHARE_ONLY,
                message = "Screen sharing may be available, but MediaProjection is not a virtual camera.",
                consumerApps = "Only destinations that explicitly accept screen sharing."
            )
            CameraCapability.OutputRoute.UNAVAILABLE -> Status(
                route = CameraCapability.OutputRoute.UNAVAILABLE,
                message = "No supported Android-wide camera output route is currently exposed.",
                consumerApps = "None detected."
            )
        }
    }
}
