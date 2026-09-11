package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSessionPolicyTest {
    @Test
    fun leavingActivityDoesNotStopTheCameraSession() {
        val policy = CameraSessionPolicy()
        policy.start("avatar-a")
        policy.onActivityHidden()
        assertTrue(policy.isRunning)
        assertTrue(policy.isReferenceLocked)
        assertFalse(policy.shouldReleaseCamera)
        assertTrue(policy.isReferenceLockedTo("avatar-a"))
    }

    @Test
    fun onlyExplicitStopReleasesCameraAndReference() {
        val policy = CameraSessionPolicy()
        policy.start("avatar-a")
        policy.onActivityHidden()
        policy.stop()
        assertFalse(policy.isRunning)
        assertFalse(policy.isReferenceLocked)
        assertTrue(policy.shouldReleaseCamera)
    }

    @Test
    fun networkChangesNeverReplaceTheActiveReference() {
        val policy = CameraSessionPolicy()
        policy.start("avatar-a")
        policy.onNetworkChanged()
        policy.onActivityHidden()
        policy.onNetworkChanged()
        assertTrue(policy.isReferenceLockedTo("avatar-a"))
    }

    @Test
    fun liveScreenRequiresPreviewAnalysisVideoAndAudio() {
        val policy = CameraSessionPolicy()
        assertTrue(policy.requiresPreview)
        assertTrue(policy.requiresAnalysis)
        assertTrue(policy.requiresVideoCapture)
        assertTrue(policy.requiresAudio)
    }
}
