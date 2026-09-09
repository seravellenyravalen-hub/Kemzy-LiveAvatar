package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCameraCapabilityTest {
    @Test
    fun unsupportedDeviceIsNeverReportedAsSystemCamera() {
        val capability = VirtualCameraCapability(
            apiLevel = 35,
            virtualDeviceManagerPresent = false,
            virtualCameraSupported = false,
            createVirtualDevicePermissionGranted = false
        )

        assertFalse(capability.platformSupported)
        assertFalse(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.UNAVAILABLE, capability.status)
    }

    @Test
    fun platformSupportWithoutPrivilegedRegistrationIsNotClaimedAsAvailable() {
        val capability = VirtualCameraCapability(
            apiLevel = 35,
            virtualDeviceManagerPresent = true,
            virtualCameraSupported = true,
            createVirtualDevicePermissionGranted = false
        )

        assertTrue(capability.platformSupported)
        assertFalse(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.PRIVILEGE_REQUIRED, capability.status)
    }

    @Test
    fun privilegedSupportedPlatformIsReportedAsAvailable() {
        val capability = VirtualCameraCapability(
            apiLevel = 35,
            virtualDeviceManagerPresent = true,
            virtualCameraSupported = true,
            createVirtualDevicePermissionGranted = true
        )

        assertTrue(capability.platformSupported)
        assertTrue(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.AVAILABLE, capability.status)
    }

    @Test
    fun apiBelowVirtualCameraSupportIsUnavailable() {
        val capability = VirtualCameraCapability(
            apiLevel = 34,
            virtualDeviceManagerPresent = true,
            virtualCameraSupported = true,
            createVirtualDevicePermissionGranted = true
        )

        assertFalse(capability.platformSupported)
        assertFalse(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.UNAVAILABLE, capability.status)
    }
}
