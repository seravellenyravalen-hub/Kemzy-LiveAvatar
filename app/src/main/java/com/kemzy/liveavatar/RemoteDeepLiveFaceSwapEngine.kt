package com.kemzy.liveavatar

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Remote Deep-Live-Cam engine. The phone keeps the camera and UI local while the
 * actual InsightFace + INSwapper inference runs in the Hugging Face Space.
 *
 * The backend is deliberately frame-based rather than pretending to be a system
 * virtual camera. Android does not expose a normal third-party virtual-camera API.
 */
class RemoteDeepLiveFaceSwapEngine(
    private val context: Context,
    private val spaceBaseUrl: String = DEFAULT_SPACE_URL
) : FaceSwapEngine {
    companion object {
        const val DEFAULT_SPACE_URL = "https://seravellenyravalen-kemzy-deep-live-avatar.hf.space"
        private const val PREPARE_ENDPOINT = "prepare_avatar"
        private const val PROCESS_ENDPOINT = "process_frame"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_FRAME_BYTES = 700_000
    }

    override var avatarUri: String? = null
        private set
    override var state: LiveFaceEngineState = LiveFaceEngineState.Idle
        private set

    private var sessionId: String? = null

    override fun setAvatar(uri: String) {
        check(state !is LiveFaceEngineState.Ready) { "Reference cannot change while the engine is live" }
        avatarUri = uri
        sessionId = null
        state = LiveFaceEngineState.Preparing
    }

    override fun prepareAvatar(): LiveFaceEngineState {
        val reference = avatarUri
        if (reference.isNullOrBlank()) {
            state = LiveFaceEngineState.Fallback("No avatar selected")
            return state
        }
        return try {
            state = LiveFaceEngineState.Preparing
            val compressed = readAndCompressReference(reference)
            val uploadedPath = upload(compressed, "kemzy-source.jpg")
            val result = callEndpoint(
                PREPARE_ENDPOINT,
                JSONArray().put(fileData(uploadedPath, "kemzy-source.jpg"))
            )
            val id = result.optString(0)
            if (id.isBlank()) error("Deep-Live-Cam backend returned no session")
            sessionId = id
            state = LiveFaceEngineState.Ready
            state
        } catch (error: Exception) {
            sessionId = null
            state = LiveFaceEngineState.Fallback("Deep-Live-Cam connection failed: ${error.message ?: "unknown error"}")
            state
        }
    }

    override fun processFrame(frame: Bitmap, tracking: FaceTrackingResult): FaceSwapFrame {
        if (state !is LiveFaceEngineState.Ready) return FaceSwapFrame.fallback("Deep-Live-Cam backend is not ready")
        val sid = sessionId ?: return FaceSwapFrame.fallback("Deep-Live-Cam session is missing")
        return try {
            val jpeg = compressFrame(frame)
            val uploadedPath = upload(jpeg, "frame-${UUID.randomUUID()}.jpg")
            val result = callEndpoint(
                PROCESS_ENDPOINT,
                JSONArray()
                    .put(sid)
                    .put(fileData(uploadedPath, "frame.jpg"))
            )
            val output = result.optJSONObject(0)
                ?: throw IllegalStateException("Backend returned no processed frame")
            val outputPath = output.optString("path").ifBlank {
                throw IllegalStateException("Backend returned no output path")
            }
            val bitmap = downloadOutput(outputPath)
            FaceSwapFrame.neural(bitmap)
        } catch (error: Exception) {
            FaceSwapFrame.fallback("Remote inference failed: ${error.message ?: "unknown error"}")
        }
    }

    override fun processFrame(tracking: FaceTrackingResult): FaceSwapFrame =
        FaceSwapFrame.fallback("Camera frame required")

    override fun clearAvatar() {
        avatarUri = null
        sessionId = null
        state = LiveFaceEngineState.Idle
    }

    override fun close() {
        sessionId = null
        if (state is LiveFaceEngineState.Ready) state = LiveFaceEngineState.Idle
    }

    private fun fileData(path: String, originalName: String): JSONObject =
        JSONObject()
            .put("path", path)
            .put("orig_name", originalName)
            .put("meta", JSONObject().put("_type", "gradio.FileData"))

    private fun upload(bytes: ByteArray, name: String): String {
        val connection = open("/gradio_api/upload", "POST")
        val boundary = "----KemzyBoundary${System.nanoTime()}"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        connection.outputStream.use { out ->
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"files\"; filename=\"$name\"\r\n".toByteArray())
            out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val response = readResponse(connection)
        val paths = JSONArray(response)
        return paths.getString(0)
    }

    private fun callEndpoint(endpoint: String, data: JSONArray): JSONArray {
        val connection = open("/gradio_api/call/$endpoint", "POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val body = JSONObject().put("data", data).toString().toByteArray()
        connection.outputStream.use { it.write(body) }
        val response = JSONObject(readResponse(connection))
        val eventId = response.optString("event_id").ifBlank {
            throw IllegalStateException("Backend did not return an event id")
        }

        val poll = open("/gradio_api/call/$endpoint/$eventId", "GET")
        val sse = readResponse(poll)
        var event = ""
        var dataLine: String? = null
        for (line in sse.split('\n')) {
            when {
                line.startsWith("event:") -> event = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    dataLine = line.substringAfter(':').trim()
                    if (event == "complete") break
                    if (event == "error") throw IllegalStateException(dataLine ?: "backend error")
                }
            }
        }
        if (event == "error") throw IllegalStateException(dataLine ?: "backend error")
        return JSONArray(dataLine ?: throw IllegalStateException("Backend returned no result"))
    }

    private fun downloadOutput(path: String): Bitmap {
        val encoded = java.net.URLEncoder.encode(path, "UTF-8")
        val connection = open("/gradio_api/file=$encoded", "GET")
        connection.inputStream.use { input ->
            return android.graphics.BitmapFactory.decodeStream(input)
                ?: throw IllegalStateException("Processed frame could not be decoded")
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val base = spaceBaseUrl.trimEnd('/')
        return (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json, text/event-stream, image/*")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream: InputStream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code ${text.take(240)}")
        return text
    }

    private fun readAndCompressReference(uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        val file = File(uri.path ?: throw IllegalArgumentException("Invalid avatar path"))
        require(file.isFile) { "Avatar file is unavailable" }
        return compressBitmap(android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Avatar could not be decoded"), 900)
    }

    private fun compressFrame(bitmap: Bitmap): ByteArray = compressBitmap(bitmap, 640)

    private fun compressBitmap(source: Bitmap, maxDimension: Int): ByteArray {
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height).toFloat())
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        ) else source
        var quality = 78
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 8
        } while (bytes.size > MAX_FRAME_BYTES && quality >= 38)
        if (resized !== source) resized.recycle()
        return bytes
    }
}
