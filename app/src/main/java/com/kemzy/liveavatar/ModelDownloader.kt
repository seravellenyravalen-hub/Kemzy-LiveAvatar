package com.kemzy.liveavatar

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class ModelDownloader(context: Context) {
    private val store = ModelStore(context.applicationContext)

    fun downloadRequired(onProgress: (modelId: String, percent: Int) -> Unit = { _, _ -> }) {
        FaceModelManifest.required.forEach { model ->
            if (store.isPresent(model)) return@forEach
            val url = model.remoteUrl ?: error("No download URL for ${model.id}")
            download(model, url, onProgress)
        }
    }

    private fun download(model: FaceModelDescriptor, url: String, onProgress: (String, Int) -> Unit) {
        val destination = store.fileFor(model)
        val temp = File(destination.parentFile, "${destination.name}.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Kemzy-LiveAvatar/0.1")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Model download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0) {
                            val percent = min(100L, readTotal * 100L / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(model.id, percent)
                            }
                        }
                    }
                }
            }
            check(temp.length() > 0L) { "Downloaded model is empty: ${model.id}" }
            check(temp.renameTo(destination)) { "Could not finalize model: ${model.id}" }
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}
