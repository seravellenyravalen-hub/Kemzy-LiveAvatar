package com.kemzy.liveavatar

data class FaceModelDescriptor(
    val id: String,
    val fileName: String,
    val required: Boolean
)

object FaceModelManifest {
    val required = listOf(
        FaceModelDescriptor("face-detector", "detector.onnx", true),
        FaceModelDescriptor("arcface-embedder", "arcface.onnx", true),
        FaceModelDescriptor("face-swapper", "inswapper.onnx", true)
    )

    val optional = listOf(
        FaceModelDescriptor("expression-restorer", "expression-restorer.onnx", false)
    )

    val all: List<FaceModelDescriptor>
        get() = required + optional
}
