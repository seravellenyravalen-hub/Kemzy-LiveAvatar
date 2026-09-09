package com.kemzy.liveavatar

import android.content.Context

class LiveSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("live_session", Context.MODE_PRIVATE)

    val isActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)

    val reference: String?
        get() = prefs.getString(KEY_REFERENCE, null)

    fun markActive(reference: String) {
        prefs.edit().putBoolean(KEY_ACTIVE, true).putString(KEY_REFERENCE, reference).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACTIVE = "active"
        private const val KEY_REFERENCE = "reference"
    }
}
