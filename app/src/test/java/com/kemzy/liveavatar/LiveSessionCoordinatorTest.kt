package com.kemzy.liveavatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionCoordinatorTest {
    @Test
    fun referenceSurvivesBackgroundAndNetworkEvents() {
        val coordinator = LiveSessionCoordinator()
        assertTrue(coordinator.start("private/avatar.jpg"))
        coordinator.onActivityHidden()
        coordinator.onNetworkChanged()
        assertTrue(coordinator.isActive)
        assertTrue(coordinator.isReferenceLockedTo("private/avatar.jpg"))
    }

    @Test
    fun stopIsIdempotentAndReleasesReference() {
        val coordinator = LiveSessionCoordinator()
        assertTrue(coordinator.start("private/avatar.jpg"))
        coordinator.stop()
        coordinator.stop()
        assertFalse(coordinator.isActive)
        assertFalse(coordinator.isReferenceLockedTo("private/avatar.jpg"))
    }
}
