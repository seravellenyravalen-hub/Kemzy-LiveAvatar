package com.kemzy.liveavatar

/** Opens each model through ONNX Runtime one at a time and checks the canonical generator hash. */
class LivePortraitModelValidator(private val repository: ModelRepository) {
    data class ModelCheck(
        val name: String,
        val bytes: Long,
        val valid: Boolean,
        val inputs: Set<String> = emptySet(),
        val outputs: Set<String> = emptySet(),
        val error: String? = null
    )

    data class BundleCheck(val models: List<ModelCheck>) {
        val complete: Boolean get() = models.size == LivePortraitModelSpec.requiredNames.size && models.all { it.valid }
        val missing: List<String> get() = models.filterNot { it.valid }.map { it.name }
    }

    fun validate(): BundleCheck {
        val checks = LivePortraitModelSpec.requiredNames.map { name ->
            val file = repository.model(name)
            if (file == null) {
                ModelCheck(name, 0L, false, error = "missing")
            } else if (name == LivePortraitModelSpec.warpingSpade && !ModelIntegrity.canonicalGeneratorMatches(file)) {
                ModelCheck(name, file.length(), false, error = "SHA-256 does not match canonical warping_spade-fix.onnx")
            } else {
                runCatching {
                    OnnxInferenceEngine.fromFile(file, name).use { engine ->
                        ModelCheck(name, file.length(), true, engine.inputNames(), engine.outputNames())
                    }
                }.getOrElse { error ->
                    ModelCheck(name, file.length(), false, error = error.message ?: error.javaClass.simpleName)
                }
            }
        }
        return BundleCheck(checks)
    }
}
