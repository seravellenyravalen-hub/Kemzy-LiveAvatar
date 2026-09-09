package com.kemzy.liveavatar

data class FaceModelDescriptor(
    val id: String,
    val fileName: String,
    val required: Boolean,
    val remoteUrl: String? = null
)

/**
 * Runtime models used by Kemzy-LiveAvatar's Android adaptation of the
 * Deep-Live-Cam/InsightFace live-swap pipeline.
 *
 * The ArcFace identity model extracts the exact identity embedding from the
 * selected source photo. INSwapper then transfers that identity to the live
 * target face. No generated avatar image is used in the inference path.
 */
object FaceModelManifest {
    val required = listOf(
        FaceModelDescriptor(
            id = "arcface-embedder",
            fileName = "w600k_r50.onnx",
            required = true,
            remoteUrl = "https://huggingface.co/leonelhs/insightface/resolve/main/w600k_r50.onnx"
        ),
        FaceModelDescriptor(
            id = "face-swapper",
            fileName = "inswapper_128.onnx",
            required = true,
            remoteUrl = "https://huggingface.co/leonelhs/insightface/resolve/main/inswapper_128.onnx"
        )
    )

    val optional = emptyList<FaceModelDescriptor>()

    val all: List<FaceModelDescriptor>
        get() = required
}
