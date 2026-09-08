package com.kemzy.liveavatar

import android.os.Build

object DeviceDiagnostics {
    fun summary(): String = buildString {
        append("Android ").append(Build.VERSION.SDK_INT)
        append(" • ABI ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        append(" • SOC ").append(systemProperty("ro.soc.manufacturer") ?: "unknown")
        append(" ").append(systemProperty("ro.soc.model") ?: "unknown")
    }

    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
