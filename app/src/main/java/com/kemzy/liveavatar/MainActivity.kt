package com.kemzy.liveavatar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val sessionController = SessionController()
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        if (hasCameraPermission()) {
            updateControls()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(
            previewView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            text = "Camera ready"
            setPadding(16, 16, 16, 16)
        }
        root.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 24)
        }

        startButton = Button(this).apply {
            text = "Start"
            setOnClickListener { startSession() }
        }
        stopButton = Button(this).apply {
            text = "Stop"
            setOnClickListener { stopSession() }
        }

        controls.addView(startButton)
        controls.addView(stopButton)
        root.addView(controls)

        setContentView(root)
    }

    private fun startSession() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }

        sessionController.start()
        bindFrontCamera()
        updateControls()
    }

    private fun stopSession() {
        sessionController.stop()
        cameraProvider?.unbindAll()
        updateControls()
    }

    private fun bindFrontCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateControls() {
        val running = sessionController.state is SessionState.Running
        startButton.isEnabled = hasCameraPermission() && !running
        stopButton.isEnabled = running
        statusView.text = if (running) {
            "Live session running"
        } else if (hasCameraPermission()) {
            "Camera ready — press Start"
        } else {
            "Camera permission required"
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            updateControls()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        sessionController.stop()
        super.onDestroy()
    }

    private companion object {
        const val CAMERA_PERMISSION_REQUEST = 1001
    }
}
