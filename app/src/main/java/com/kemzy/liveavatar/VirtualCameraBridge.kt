package com.kemzy.liveavatar

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.camera.VirtualCamera
import android.companion.virtual.camera.VirtualCameraCallback
import android.companion.virtual.camera.VirtualCameraConfig
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraMetadata
import android.media.Image
import android.media.ImageWriter
import android.os.Build
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Real Android 15 virtual-camera producer.
 *
 * This class never reports success until VirtualDevice + VirtualCamera are actually created.
 * On ordinary/OEM builds where the privileged virtual-device role is unavailable, creation
 * fails and the caller can expose that state instead of pretending to be a system camera.
 */
class VirtualCameraBridge(
    private val service: AvatarStreamingService,
    private val executor: Executor
) {
    enum class State { STOPPED, WAITING_FOR_ASSOCIATION, REGISTERING, REGISTERED, FAILED }

    @Volatile
    var state: State = State.STOPPED
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val writers = ConcurrentHashMap<Int, ImageWriter>()
    private var virtualDevice: VirtualDeviceManager.VirtualDevice? = null
    private var virtualCamera: VirtualCamera? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            fail("Android 15 or newer is required")
            return
        }
        if (!VirtualCameraCapability.probe(service).isSupported) {
            fail("Virtual camera platform or registration privilege is unavailable")
            return
        }
        state = State.REGISTERING
        lastError = null
        executor.execute { associateAndCreate() }
    }

    fun stop() {
        writers.values.forEach { runCatching { it.close() } }
        writers.clear()
        runCatching { virtualCamera?.close() }
        virtualCamera = null
        runCatching { virtualDevice?.close() }
        virtualDevice = null
        state = State.STOPPED
    }

    @SuppressLint("MissingPermission")
    private fun associateAndCreate() {
        val cdm = service.getSystemService(CompanionDeviceManager::class.java)
        val existing = cdm?.myAssociations?.firstOrNull {
            it.packageName == service.packageName &&
                it.displayName?.toString() == CAMERA_NAME
        }

        if (existing != null) {
            createVirtualDevice(existing)
            return
        }

        state = State.WAITING_FOR_ASSOCIATION
        val request = AssociationRequest.Builder()
            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_APP_STREAMING)
            .setDisplayName(CAMERA_NAME)
            .setSelfManaged(true)
            .build()

        cdm?.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: android.content.IntentSender) {
                try {
                    service.startIntentSender(
                        intentSender,
                        null,
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
                        0,
                        0
                    )
                } catch (error: Exception) {
                    fail("Virtual-camera association UI could not start: ${error.message}")
                }
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                createVirtualDevice(associationInfo)
            }

            override fun onFailure(error: CharSequence?) {
                fail("Virtual-camera association failed: $error")
            }
        }, null)
    }

    @SuppressLint("MissingPermission")
    private fun createVirtualDevice(association: AssociationInfo) {
        try {
            state = State.REGISTERING
            val manager = service.getSystemService(VirtualDeviceManager::class.java)
                ?: throw IllegalStateException("VirtualDeviceManager is unavailable")

            virtualDevice = manager.createVirtualDevice(
                association.id,
                VirtualDeviceParams.Builder()
                    .setName(CAMERA_NAME)
                    .setDevicePolicy(
                        VirtualDeviceParams.POLICY_TYPE_CAMERA,
                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM
                    )
                    .build()
            )

            val config = VirtualCameraConfig.Builder(CAMERA_NAME)
                .addStreamConfig(640, 480, ImageFormat.YUV_420_888, 30)
                .setLensFacing(CameraMetadata.LENS_FACING_FRONT)
                .setVirtualCameraCallback(executor, callback)
                .build()

            virtualCamera = virtualDevice!!.createVirtualCamera(config)
            state = State.REGISTERED
        } catch (error: SecurityException) {
            fail("Android denied virtual-camera registration: ${error.message}")
        } catch (error: Exception) {
            fail("Virtual-camera registration failed: ${error.message}")
        }
    }

    private val callback = object : VirtualCameraCallback {
        override fun onStreamConfigured(
            streamId: Int,
            surface: Surface,
            width: Int,
            height: Int,
            format: Int
        ) {
            if (format != ImageFormat.YUV_420_888) {
                Log.w(TAG, "Unsupported virtual-camera stream format: $format")
                return
            }
            runCatching {
                writers[streamId]?.close()
                writers[streamId] = ImageWriter.newInstance(surface, 3, ImageFormat.YUV_420_888)
            }.onFailure { fail("Could not open virtual-camera stream: ${it.message}") }
        }

        override fun onProcessCaptureRequest(streamId: Int, frameId: Long) {
            val writer = writers[streamId] ?: return
            val frame = StreamingFrameBus.snapshot() ?: return
            try {
                writeBitmap(writer, frame)
            } catch (error: Exception) {
                Log.w(TAG, "Virtual-camera frame $frameId failed", error)
            } finally {
                frame.recycle()
            }
        }

        override fun onStreamClosed(streamId: Int) {
            writers.remove(streamId)?.close()
        }
    }

    private fun writeBitmap(writer: ImageWriter, bitmap: Bitmap) {
        val image = writer.dequeueInputImage()
        try {
            bitmapToYuv420(bitmap, image)
            writer.queueInputImage(image)
        } catch (error: Exception) {
            image.close()
            throw error
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, image: Image) {
        val width = image.width
        val height = image.height
        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        try {
            val argb = IntArray(width * height)
            scaled.getPixels(argb, 0, width, 0, 0, width, height)
            val planes = image.planes
            fillY(planes[0], argb, width, height)
            fillChroma(planes[1], argb, width, height, true)
            fillChroma(planes[2], argb, width, height, false)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun fillY(plane: Image.Plane, pixels: IntArray, width: Int, height: Int) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (y in 0 until height) {
            val row = y * rowStride
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                buffer.put(row + x * pixelStride, ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
            }
        }
    }

    private fun fillChroma(
        plane: Image.Plane,
        pixels: IntArray,
        width: Int,
        height: Int,
        isU: Boolean
    ) {
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (y in 0 until height step 2) {
            val row = (y / 2) * rowStride
            for (x in 0 until width step 2) {
                val color = pixels[y * width + x]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val value = if (isU) {
                    ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                } else {
                    ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                }
                buffer.put(row + (x / 2) * pixelStride, value.coerceIn(0, 255).toByte())
            }
        }
    }

    private fun fail(message: String) {
        lastError = message
        state = State.FAILED
        Log.w(TAG, message)
    }

    companion object {
        const val CAMERA_NAME = "Kemzy-LiveAvatar"
        private const val TAG = "KemzyVirtualCamera"
    }
}
