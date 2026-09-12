package com.kemzy.liveavatar

/**
 * Opens each model through ONNX Runtime one at a time. This validates the binary
 * and records its graph interface without keeping the complete bundle resident.
 */
class LivePortraitModelValidator(
    private val repository: ModelRepository
) {
    data class ModelCheck(
        val name: String,
        val bytes: Long,
        val valid: Boolean,
        val inputs: Set<String> = emptySet(),
        val outputs: Set<String> = emptySet(),
        val error: String? = null
    )

    data class BundleCheck(
        val models: List<ModelCheck>
    ) {
        val complete: Boolean
            get() = models.size == LivePortraitModelSpec.requiredNames.size && models.all { it.valid }

        val missing: List<String>
            get() = models.filterNot { it.valid }.map { it.name }
    }

    fun validate(): BundleCheck {
        val checks = LivePortraitModelSpec.requiredNames.map { name ->
            val file = repository.model(name)
            if (file == null) {
                ModelCheck(name = name, bytes = 0L, valid = false, error = "missing")
            } else {
                runCatching {
                    OnnxInferenceEngine.fromFile(file, name).use { engine ->
                        ModelCheck(
                            name = name,
                            bytes = file.length(),
                            valid = true,
                            inputs = engine.inputNames(),
                            outputs = engine.outputNames()
                        )
                    }
                }.getOrElse { error ->
                    ModelCheck(
                        name = name,
                        bytes = file.length(),
                        valid = false,
                        error = error.message ?: error.javaClass.simpleName
                    )
                }
            }
        }
        return BundleCheck(checks)
    }
}
