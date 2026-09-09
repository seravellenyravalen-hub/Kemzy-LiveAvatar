package com.kemzy.liveavatar

import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualCameraOutputTest {
    @Test
    fun unsupportedDeviceDoesNotClaimVirtualCamera() {
        val report = CameraCapability.Report(
            apiLevel = 35,
            virtualDeviceManagerAvailable = false,
            virtualCameraApiPresent = false,
            virtualCameraSupported = null,
            externalCameraPresent = false,
            overlayPermissionGranted = false,
            cameraPermissionGranted = true,
            backgroundCameraServicePossible = true
        )

        assertEquals(
            VirtualCameraOutput.State.NOT_SUPPORTED,
            VirtualCameraOutput.inspect(report).state
        )
    }

    @Test
    fun nativeSupportIsPreferredWhenReported() {
        val report = CameraCapability.Report(
            apiLevel = 35,
            virtualDeviceManagerAvailable = true,
            virtualCameraApiPresent = true,
            virtualCameraSupported = true,
            externalCameraPresent = true,
            overlayPermissionGranted = true,
            cameraPermissionGranted = true,
            backgroundCameraServicePossible = true
        )

        assertEquals(
            VirtualCameraOutput.State.READY_FOR_NATIVE_PROVIDER,
            VirtualCameraOutput.inspect(report).state
        )
        assertEquals(
            CameraCapability.OutputRoute.NATIVE_VIRTUAL_CAMERA,
            report.bestRoute()
        )
    }
}
