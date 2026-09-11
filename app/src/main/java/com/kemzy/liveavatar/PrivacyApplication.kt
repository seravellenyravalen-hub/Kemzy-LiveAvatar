package com.kemzy.liveavatar

import android.app.Application
import java.util.concurrent.atomic.AtomicBoolean

class PrivacyApplication : Application() {
    private val protectedSession = AtomicBoolean(false)

    val unlocked: Boolean
        get() = protectedSession.get()

    override fun onCreate() {
        super.onCreate()
    }

    fun markUnlocked() {
        protectedSession.set(true)
    }

    fun lock() {
        protectedSession.set(false)
    }
}
