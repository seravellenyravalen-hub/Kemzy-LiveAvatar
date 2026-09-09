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
            virtualCameraSupported = false
        )

        assertFalse(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.UNAVAILABLE, capability.status)
    }

    @Test
    fun supportedPlatformIsReportedAsAvailable() {
        val capability = VirtualCameraCapability(
            apiLevel = 35,
            virtualDeviceManagerPresent = true,
            virtualCameraSupported = true
        )

        assertTrue(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.AVAILABLE, capability.status)
    }

    @Test
    fun apiBelowVirtualCameraSupportIsUnavailable() {
        val capability = VirtualCameraCapability(
            apiLevel = 34,
            virtualDeviceManagerPresent = true,
            virtualCameraSupported = true
        )

        assertFalse(capability.isSupported)
        assertEquals(VirtualCameraCapability.Status.UNAVAILABLE, capability.status)
    }
}
