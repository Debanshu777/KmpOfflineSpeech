package io.github.debanshu.siren.speech

/** A host-packaged file or a caller-supplied local filesystem path. */
public sealed interface ModelFile {
    public val path: String

    public data class Bundled(override val path: String) : ModelFile {
        init {
            require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
                path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "A bundled model path must be relative and cannot traverse directories"
            }
        }
    }

    public data class Local(override val path: String) : ModelFile {
        init { require(path.isNotBlank()) { "A local model path is required" } }
    }
}

/** Files must be a compatible sherpa Whisper export, not an arbitrary ONNX graph. */
public data class WhisperModel(
    public val encoder: ModelFile,
    public val decoder: ModelFile,
    public val tokens: ModelFile,
    public val language: String = "en",
    public val task: String = "transcribe",
    public val displayName: String = "Whisper",
) {
    init { require(task == "transcribe" || task == "translate") }
}

public data class KittenModel(
    public val model: ModelFile,
    public val voices: ModelFile,
    public val tokens: ModelFile,
    public val lexicon: ModelFile,
    public val speakerId: Int = 0,
    public val speed: Float = 1f,
    public val displayName: String = "Kitten TTS",
) {
    init {
        require(speakerId >= 0)
        require(speed.isFinite() && speed > 0f)
    }
}

/** Null removes that capability. Descriptors themselves never download or load models. */
public data class SherpaModels(
    public val recognition: WhisperModel? = null,
    public val synthesis: KittenModel? = null,
    public val threads: Int = 2,
) {
    init { require(threads > 0) }
}
