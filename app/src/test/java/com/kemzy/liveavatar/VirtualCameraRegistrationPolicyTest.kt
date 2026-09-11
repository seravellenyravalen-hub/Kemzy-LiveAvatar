package com.kemzy.liveavatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCameraRegistrationPolicyTest {
    @Test
    fun stockAndroid15CapabilityWithoutPrivilegedPermissionCannotRegister() {
        val policy = VirtualCameraRegistrationPolicy(
            apiLevel = 35,
            platformVirtualCameraSupported = true,
            createVirtualDevicePermissionGranted = false
        )

        assertTrue(policy.platformSupported)
        assertFalse(policy.canRegister)
        assertEquals(
            VirtualCameraRegistrationPolicy.Status.PRIVILEGE_REQUIRED,
            policy.status
        )
    }

    @Test
    fun privilegedAndroid15PlatformCanRegister() {
        val policy = VirtualCameraRegistrationPolicy(
            apiLevel = 35,
            platformVirtualCameraSupported = true,
            createVirtualDevicePermissionGranted = true
        )

        assertTrue(policy.platformSupported)
        assertTrue(policy.canRegister)
        assertEquals(
            VirtualCameraRegistrationPolicy.Status.READY,
            policy.status
        )
    }

    @Test
    fun unsupportedPlatformCannotRegisterEvenIfPermissionIsPresent() {
        val policy = VirtualCameraRegistrationPolicy(
            apiLevel = 35,
            platformVirtualCameraSupported = false,
            createVirtualDevicePermissionGranted = true
        )

        assertFalse(policy.platformSupported)
        assertFalse(policy.canRegister)
        assertEquals(
            VirtualCameraRegistrationPolicy.Status.UNSUPPORTED,
            policy.status
        )
    }
}
