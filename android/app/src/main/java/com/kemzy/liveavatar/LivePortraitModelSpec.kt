package com.kemzy.liveavatar

/** Canonical model manifest for the Android LivePortrait pipeline. */
object LivePortraitModelSpec {
    const val appearance = "appearance_feature_extractor.onnx"
    const val motion = "motion_extractor.onnx"
    const val warpingSpade = "warping_spade-fix.onnx"
    const val stitching = "stitching.onnx"
    const val stitchingEye = "stitching_eye.onnx"
    const val stitchingLip = "stitching_lip.onnx"
    const val landmark = "landmark.onnx"
    const val retinaFace = "retinaface_det_static.onnx"
    const val face2d106 = "face_2dpose_106_static.onnx"

    const val warpingSpadeSha256 = "1bae996fef2fe742d87f10b88c1b9c8fb601fd283ab004e3efa49def9fe58c2d"

    val requiredNames: List<String> = listOf(
        appearance, motion, warpingSpade, stitching, stitchingEye, stitchingLip,
        landmark, retinaFace, face2d106
    )

    data class Check(val name: String, val present: Boolean, val bytes: Long)

    fun check(files: Map<String, java.io.File>): List<Check> = requiredNames.map { name ->
        val file = files[name]
        Check(name, file?.isFile == true && file.length() > 0L, file?.length() ?: 0L)
    }
}
