package com.kemzy.liveavatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val appLock by lazy { (application as KemzyApplication).privacyLock }

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) finish()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else status.text = "Camera permission is required for Live mode."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.cameraPreview)
        status = findViewById(R.id.statusText)
    }

    override fun onResume() {
        super.onResume()
        if (appLock.isLocked()) {
            lockLauncher.launch(Intent(this, LockActivity::class.java))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        stopCamera()
        appLock.lock()
        super.onPause()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (isFinishing || isDestroyed || appLock.isLocked()) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            status.text = "Live camera ready"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        ProcessCameraProvider.getInstance(this).get().unbindAll()
    }
}
