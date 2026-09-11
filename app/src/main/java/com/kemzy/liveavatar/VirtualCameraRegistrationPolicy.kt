package com.kemzy.liveavatar

/**
 * Separates platform support from the privileged registration gate used by Android's
 * virtual-camera service.
 *
 * Android exposes virtual cameras through the system VirtualDevice stack. The platform
 * registration path requires CREATE_VIRTUAL_DEVICE; a normal third-party APK cannot
 * manufacture that privilege by declaring it in its manifest.
 */
class VirtualCameraRegistrationPolicy(
    val apiLevel: Int,
    val platformVirtualCameraSupported: Boolean,
    val createVirtualDevicePermissionGranted: Boolean
) {
    enum class Status {
        READY,
        PRIVILEGE_REQUIRED,
        UNSUPPORTED
    }

    val platformSupported: Boolean
        get() = apiLevel >= 35 && platformVirtualCameraSupported

    val canRegister: Boolean
        get() = platformSupported && createVirtualDevicePermissionGranted

    val status: Status
        get() = when {
            !platformSupported -> Status.UNSUPPORTED
            !createVirtualDevicePermissionGranted -> Status.PRIVILEGE_REQUIRED
            else -> Status.READY
        }
}
