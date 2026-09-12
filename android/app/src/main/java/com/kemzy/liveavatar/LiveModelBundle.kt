package com.kemzy.liveavatar

import java.io.File

/** All files required by the Android LivePortrait ONNX pipeline. */
data class LiveModelBundle(
    val appearance: File?,
    val motion: File?,
    val warpingSpade: File?,
    val stitching: File?,
    val stitchingEye: File?,
    val stitchingLip: File?,
    val landmark: File?,
    val retinaFace: File?,
    val face2d106: File?
) {
    val isComplete: Boolean
        get() = listOf(
            appearance,
            motion,
            warpingSpade,
            stitching,
            stitchingEye,
            stitchingLip,
            landmark,
            retinaFace,
            face2d106
        ).all { it.isUsable() }

    fun missingRoles(): List<String> = LivePortraitModelSpec.check(files()).filterNot { it.present }.map { it.name }

    fun files(): Map<String, File> = listOfNotNull(
        appearance?.let { LivePortraitModelSpec.appearance to it },
        motion?.let { LivePortraitModelSpec.motion to it },
        warpingSpade?.let { LivePortraitModelSpec.warpingSpade to it },
        stitching?.let { LivePortraitModelSpec.stitching to it },
        stitchingEye?.let { LivePortraitModelSpec.stitchingEye to it },
        stitchingLip?.let { LivePortraitModelSpec.stitchingLip to it },
        landmark?.let { LivePortraitModelSpec.landmark to it },
        retinaFace?.let { LivePortraitModelSpec.retinaFace to it },
        face2d106?.let { LivePortraitModelSpec.face2d106 to it }
    ).toMap()

    private fun File?.isUsable(): Boolean = this != null && isFile && length() > 0L
}

class LiveModelBundleRepository(
    private val modelRepository: ModelRepository
) {
    fun inspect(): LiveModelBundle = LiveModelBundle(
        appearance = modelRepository.model(LivePortraitModelSpec.appearance),
        motion = modelRepository.model(LivePortraitModelSpec.motion),
        warpingSpade = modelRepository.model(LivePortraitModelSpec.warpingSpade),
        stitching = modelRepository.model(LivePortraitModelSpec.stitching),
        stitchingEye = modelRepository.model(LivePortraitModelSpec.stitchingEye),
        stitchingLip = modelRepository.model(LivePortraitModelSpec.stitchingLip),
        landmark = modelRepository.model(LivePortraitModelSpec.landmark),
        retinaFace = modelRepository.model(LivePortraitModelSpec.retinaFace),
        face2d106 = modelRepository.model(LivePortraitModelSpec.face2d106)
    )
}
