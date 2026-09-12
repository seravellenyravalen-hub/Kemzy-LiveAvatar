package com.kemzy.liveavatar

/**
 * Canonical model manifest for the Android LivePortrait pipeline.
 *
 * Large model binaries are deliberately kept outside Git. The application
 * imports them into its private model directory and validates the complete
 * bundle before allowing Live mode to start.
 */
object LivePortraitModelSpec {
    const val appearance = "appearance_feature_extractor.onnx"
    const val motion = "motion_extractor.onnx"
    const val warpingSpade = "warping_spade.onnx"
    const val stitching = "stitching.onnx"
    const val stitchingEye = "stitching_eye.onnx"
    const val stitchingLip = "stitching_lip.onnx"
    const val landmark = "landmark.onnx"
    const val retinaFace = "retinaface_det_static.onnx"
    const val face2d106 = "face_2dpose_106_static.onnx"

    val requiredNames: List<String> = listOf(
        appearance,
        motion,
        warpingSpade,
        stitching,
        stitchingEye,
        stitchingLip,
        landmark,
        retinaFace,
        face2d106
    )

    data class Check(
        val name: String,
        val present: Boolean,
        val bytes: Long
    )

    fun check(files: Map<String, java.io.File>): List<Check> = requiredNames.map { name ->
        val file = files[name]
        Check(name, file?.isFile == true && file.length() > 0L, file?.length() ?: 0L)
    }
}
