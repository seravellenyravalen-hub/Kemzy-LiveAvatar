package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class CameraSettingsActivity : ComponentActivity() {
    private lateinit var cameraStatus: TextView
    private lateinit var microphoneStatus: TextView
    private lateinit var virtualCameraStatus: TextView
    private lateinit var cameraButton: Button
    private lateinit var microphoneButton: Button

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshStatus()
    }

    private val requestMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF000000.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Camera & App Settings"
            textSize = 25f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 18)
        })
        root.addView(TextView(this).apply {
            text = "Kemzy-LiveAvatar needs camera access for face tracking and microphone access for recorded video audio. Android keeps these permissions under your control."
            textSize = 15f
            setTextColor(0xFFD0D0D0.toInt())
            setPadding(0, 0, 0, 20)
        })

        cameraStatus = statusText()
        root.addView(cameraStatus)
        cameraButton = Button(this).apply {
            text = "Enable Camera"
            setOnClickListener { requestCamera.launch(Manifest.permission.CAMERA) }
        }
        root.addView(cameraButton)

        microphoneStatus = statusText()
        root.addView(microphoneStatus)
        microphoneButton = Button(this).apply {
            text = "Enable Microphone"
            setOnClickListener { requestMicrophone.launch(Manifest.permission.RECORD_AUDIO) }
        }
        root.addView(microphoneButton)

        virtualCameraStatus = statusText().apply {
            setPadding(0, 22, 0, 10)
        }
        root.addView(virtualCameraStatus)
        root.addView(TextView(this).apply {
            text = "System-wide camera mode is only available when the Android firmware exposes the platform virtual-camera service. Kemzy will never show this as enabled unless the device reports that capability."
            textSize = 14f
            setTextColor(0xFFBDBDBD.toInt())
            setPadding(0, 0, 0, 16)
        })

        root.addView(Button(this).apply {
            text = "Open Android App Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        })

        root.addView(Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun statusText() = TextView(this).apply {
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.START
        setPadding(0, 14, 0, 0)
    }

    private fun refreshStatus() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        cameraStatus.text = if (cameraGranted) "Camera: ON — available to Kemzy-LiveAvatar" else "Camera: OFF — permission required"
        microphoneStatus.text = if (microphoneGranted) "Microphone: ON — available for recording" else "Microphone: OFF — permission required"
        cameraButton.isEnabled = !cameraGranted
        microphoneButton.isEnabled = !microphoneGranted

        val capability = VirtualCameraCapability.probe(this)
        virtualCameraStatus.text = if (capability.isSupported) {
            "System virtual camera: AVAILABLE — platform support detected"
        } else {
            "System virtual camera: UNAVAILABLE — this firmware does not expose a usable platform virtual camera"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::cameraStatus.isInitialized) refreshStatus()
    }
}
