package com.kemzy.liveavatar

data class FaceModelDescriptor(
    val id: String,
    val fileName: String,
    val required: Boolean,
    val remoteUrl: String? = null
)

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
        ),
        FaceModelDescriptor(
            id = "inswapper-emap",
            fileName = "emap.bin",
            required = true,
            remoteUrl = "https://raw.githubusercontent.com/Parasaran-Python/android-face-fusion/master/app/src/main/assets/emap.bin"
        )
    )

    val optional = listOf(
        FaceModelDescriptor(
            id = "expression-restorer",
            fileName = "expression-restorer.onnx",
            required = false
        )
    )

    val all: List<FaceModelDescriptor>
        get() = required + optional
}
