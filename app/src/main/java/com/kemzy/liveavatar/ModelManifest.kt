package com.kemzy.liveavatar

data class FaceModelDescriptor(
    val id: String,
    val fileName: String,
    val required: Boolean,
    val remoteUrl: String? = null
)

/**
 * Minimal on-device runtime for Kemzy-LiveAvatar.
 *
 * INSwapper requires the ArcFace w600k_r50 identity model. The FP16 swapper
 * is used to avoid the larger FP32 swapper; no enhancement model is downloaded.
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
            fileName = "inswapper_128_fp16.onnx",
            required = true,
            remoteUrl = "https://huggingface.co/hacksider/deep-live-cam/resolve/main/inswapper_128_fp16.onnx"
        )
    )

    val optional = emptyList<FaceModelDescriptor>()

    val all: List<FaceModelDescriptor>
        get() = required
}
