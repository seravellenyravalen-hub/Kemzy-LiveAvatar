package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class DeviceSetupActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var models: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refresh()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Device Setup"
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Everything required for camera, voice, background live mode and on-device AI."
            setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 18)
        })

        status = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Open App Permissions"
            setOnClickListener { openAppSettings() }
        })
        root.addView(Button(this).apply {
            text = "Open Battery / Background Settings"
            setOnClickListener { openBatterySettings() }
        })
        root.addView(Button(this).apply {
            text = "Open Notification Settings"
            setOnClickListener { openNotificationSettings() }
        })

        models = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 18, 0, 6)
        }
        root.addView(models)

        root.addView(Button(this).apply {
            text = "Prepare / Download AI Models"
            setOnClickListener { prepareModels() }
        })

        root.addView(TextView(this).apply {
            text = "Diagnostics\n${DeviceDiagnostics.summary()}"
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 18, 0, 0)
        })
        setContentView(root)
    }

    private fun refresh() {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 || NotificationManagerCompat.from(this).areNotificationsEnabled()
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        } else true
        status.text = "Camera: ${if (camera) "ON" else "OFF"}\nMicrophone: ${if (mic) "ON" else "OFF"}\nNotifications: ${if (notifications) "ON" else "OFF"}\nBattery unrestricted: ${if (battery) "YES" else "NO"}"

        val missing = ModelStore(applicationContext).missingRequiredModels()
        models.text = if (missing.isEmpty()) {
            "AI models: READY"
        } else {
            "AI models: ${missing.size} required model(s) missing\n${missing.joinToString("\n") { "• ${it.id}" }}"
        }
    }

    private fun prepareModels() {
        models.text = "AI models: downloading… Keep the app open and connected to the internet."
        executor.execute {
            runCatching { ModelDownloader(applicationContext).downloadRequired() }
                .onSuccess {
                    runOnUiThread { refresh() }
                }
                .onFailure { error ->
                    runOnUiThread { models.text = "AI model preparation failed: ${error.message ?: "unknown error"}" }
                }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(intent) }.getOrElse { openAppSettings() }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
