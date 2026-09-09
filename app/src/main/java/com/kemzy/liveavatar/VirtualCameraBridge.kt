package com.kemzy.liveavatar

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraMetadata
import android.media.Image
import android.media.ImageWriter
import android.os.Build
import android.util.Log
import android.view.Surface
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * Real Android virtual-camera producer.
 *
 * The VirtualDevice/VirtualCamera classes are SystemApi/hidden from ordinary SDK stubs,
 * so this bridge resolves them at runtime. It never reports REGISTERED until the platform
 * has actually created both the virtual device and virtual camera.
 */
class VirtualCameraBridge(
    private val service: AvatarStreamingService,
    private val executor: Executor
) {
    enum class State { STOPPED, WAITING_FOR_ASSOCIATION, REGISTERING, REGISTERED, FAILED }

    @Volatile var state: State = State.STOPPED
        private set
    @Volatile var lastError: String? = null
        private set

    private val writers = ConcurrentHashMap<Int, ImageWriter>()
    private var virtualDevice: Any? = null
    private var virtualCamera: Any? = null

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
        closeObject(virtualCamera)
        closeObject(virtualDevice)
        virtualCamera = null
        virtualDevice = null
        state = State.STOPPED
    }

    @SuppressLint("MissingPermission")
    private fun associateAndCreate() {
        val cdm = service.getSystemService(CompanionDeviceManager::class.java)
        val existing = cdm?.myAssociations?.firstOrNull {
            it.packageName == service.applicationContext.packageName &&
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
                    service.startIntentSender(intentSender, null, 0, 0, 0)
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

    private fun createVirtualDevice(association: AssociationInfo) {
        try {
            state = State.REGISTERING
            val manager = service.getSystemService("virtualdevice")
                ?: throw IllegalStateException("VirtualDeviceManager is unavailable")
            val managerClass = Class.forName("android.companion.virtual.VirtualDeviceManager")
            val paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams")
            val builderClass = Class.forName("android.companion.virtual.VirtualDeviceParams.${'$'}Builder")
            val builder = builderClass.getConstructor().newInstance()
            builderClass.getMethod("setName", String::class.java).invoke(builder, CAMERA_NAME)
            val policyType = paramsClass.getField("POLICY_TYPE_CAMERA").getInt(null)
            val customPolicy = paramsClass.getField("DEVICE_POLICY_CUSTOM").getInt(null)
            builderClass.getMethod("setDevicePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(builder, policyType, customPolicy)
            val params = builderClass.getMethod("build").invoke(builder)
            val create = managerClass.getMethod("createVirtualDevice", Int::class.javaPrimitiveType, paramsClass)
            virtualDevice = create.invoke(manager, association.id, params)

            val configClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig")
            val configBuilderClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig.${'$'}Builder")
            val configBuilder = configBuilderClass.getConstructor(String::class.java).newInstance(CAMERA_NAME)
            configBuilderClass.getMethod(
                "addStreamConfig", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).invoke(configBuilder, 640, 480, ImageFormat.YUV_420_888, 30)
            configBuilderClass.getMethod("setLensFacing", Int::class.javaPrimitiveType)
                .invoke(configBuilder, CameraMetadata.LENS_FACING_FRONT)

            val callbackClass = Class.forName("android.companion.virtual.camera.VirtualCameraCallback")
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
                callbackInvocationHandler
            )
            configBuilderClass.getMethod("setVirtualCameraCallback", Executor::class.java, callbackClass)
                .invoke(configBuilder, executor, callback)
            val config = configBuilderClass.getMethod("build").invoke(configBuilder)

            val createCamera = virtualDevice!!::class.java.getMethod("createVirtualCamera", configClass)
            virtualCamera = createCamera.invoke(virtualDevice, config)
            state = State.REGISTERED
        } catch (error: SecurityException) {
            fail("Android denied virtual-camera registration: ${error.message}")
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            fail("Virtual-camera registration failed: ${cause.message ?: cause.javaClass.simpleName}")
        }
    }

    private val callbackInvocationHandler = java.lang.reflect.InvocationHandler { _, method, args ->
        when (method.name) {
            "onStreamConfigured" -> {
                val streamId = args?.getOrNull(0) as? Int ?: return@InvocationHandler null
                val surface = args.getOrNull(1) as? Surface ?: return@InvocationHandler null
                val width = args.getOrNull(2) as? Int ?: 0
                val height = args.getOrNull(3) as? Int ?: 0
                val format = args.getOrNull(4) as? Int ?: 0
                configureStream(streamId, surface, width, height, format)
            }
            "onProcessCaptureRequest" -> {
                val streamId = args?.getOrNull(0) as? Int ?: return@InvocationHandler null
                val frameId = args.getOrNull(1) as? Long ?: 0L
                processFrame(streamId, frameId)
            }
            "onStreamClosed" -> {
                val streamId = args?.getOrNull(0) as? Int ?: return@InvocationHandler null
                writers.remove(streamId)?.close()
            }
        }
        null
    }

    private fun configureStream(streamId: Int, surface: Surface, width: Int, height: Int, format: Int) {
        if (format != ImageFormat.YUV_420_888) {
            fail("Unsupported virtual-camera stream format: $format")
            return
        }
        runCatching {
            writers[streamId]?.close()
            writers[streamId] = ImageWriter.newInstance(surface, 3, ImageFormat.YUV_420_888)
        }.onFailure { fail("Could not open virtual-camera stream: ${it.message}") }
    }

    private fun processFrame(streamId: Int, frameId: Long) {
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
        for (y in 0 until height) for (x in 0 until width) {
            val color = pixels[y * width + x]
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            buffer.put(y * rowStride + x * pixelStride, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte())
        }
    }

    private fun fillChroma(plane: Image.Plane, pixels: IntArray, width: Int, height: Int, isU: Boolean) {
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val color = pixels[y * width + x]
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            val value = if (isU) ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            else ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
            buffer.put((y / 2) * rowStride + (x / 2) * pixelStride, value.coerceIn(0, 255).toByte())
        }
    }

    private fun closeObject(value: Any?) {
        if (value == null) return
        runCatching { value.javaClass.getMethod("close").invoke(value) }
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
