package com.kemzy.liveavatar

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceTracker(
    private val onResult: (FaceTrackingResult) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    fun process(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                onResult(
                    if (face == null) {
                        FaceTrackingResult.none()
                    } else {
                        val bounds = face.boundingBox
                        val imageWidth = image.width.toFloat().coerceAtLeast(1f)
                        val imageHeight = image.height.toFloat().coerceAtLeast(1f)
                        FaceTrackingResult(
                            faceCount = faces.size,
                            yawDegrees = face.headEulerAngleY,
                            pitchDegrees = face.headEulerAngleX,
                            rollDegrees = face.headEulerAngleZ,
                            centerX = bounds.exactCenterX() / imageWidth,
                            centerY = bounds.exactCenterY() / imageHeight,
                            width = bounds.width() / imageWidth,
                            height = bounds.height() / imageHeight
                        )
                    }
                )
            }
            .addOnFailureListener { error -> onError(error) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        detector.close()
    }
}
