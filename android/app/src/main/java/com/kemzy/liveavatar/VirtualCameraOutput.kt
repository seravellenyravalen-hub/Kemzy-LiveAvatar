package com.kemzy.liveavatar

/**
 * Describes the system-wide camera publication capability without pretending
 * that a normal APK can install a Camera HAL or privileged virtual-camera
 * provider by itself.
 */
object VirtualCameraOutput {
    enum class State {
        READY_FOR_NATIVE_PROVIDER,
        SYSTEM_PROVIDER_REQUIRED,
        NOT_SUPPORTED
    }

    data class Status(
        val state: State,
        val explanation: String
    )

    fun inspect(report: CameraCapability.Report): Status = when {
        report.virtualCameraSupported == true -> Status(
            State.READY_FOR_NATIVE_PROVIDER,
            "The device reports native virtual-camera support. Kemzy can use the public virtual-device path when the required provider role/permissions are exposed by the OEM."
        )
        report.virtualDeviceManagerAvailable -> Status(
            State.SYSTEM_PROVIDER_REQUIRED,
            "VirtualDeviceManager is present, but this app cannot assume it may publish a system camera. OEM/system provider integration must expose the virtual-camera path."
        )
        else -> Status(
            State.NOT_SUPPORTED,
            "This device does not currently expose the required virtual-camera system service to Kemzy."
        )
    }
}
