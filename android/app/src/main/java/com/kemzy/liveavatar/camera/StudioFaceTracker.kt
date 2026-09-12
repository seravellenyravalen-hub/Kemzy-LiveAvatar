package com.kemzy.liveavatar.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit

class StudioFaceTracker : Closeable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    fun largestFace(bitmap: Bitmap): Face? =
        runCatching {
            Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 350L, TimeUnit.MILLISECONDS)
                .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        }.getOrNull()

    override fun close() = detector.close()
}
