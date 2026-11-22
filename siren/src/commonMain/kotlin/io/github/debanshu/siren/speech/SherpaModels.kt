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

    public companion object {
        /**
         * Uses host-packaged Whisper tiny INT8 and Kitten nano INT8 files in the sample layout.
         *
         * [directory] contains `whisper/` and `kitten/` folders. It must be a portable relative
         * resource/asset path; an empty string selects the resource root. Trailing `/` is ignored.
         * The host must supply the files: this preset never downloads or loads weights.
         * These are specific sherpa-compatible exports, not arbitrary ONNX models. Disable either
         * capability independently with [recognition] or [synthesis]; use the model constructors
         * for custom filenames, languages, voices or other compatible exports.
         */
        public fun bundled(
            directory: String = "models",
            recognition: Boolean = true,
            synthesis: Boolean = true,
            threads: Int = 2,
        ): SherpaModels {
            require(directory.isEmpty() || directory.isNotBlank()) { "A bundled model directory cannot be blank" }
            require(!directory.startsWith('/') && '\\' !in directory && ':' !in directory) {
                "A bundled model directory must be a portable relative path"
            }
            val root = directory.trimEnd('/')
            require(directory.isEmpty() || root.isNotEmpty()) { "Use an empty string for the bundled resource root" }
            require(root.isEmpty() || root.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                "A bundled model directory cannot traverse directories or contain empty segments"
            }
            return preset(
                file = { relative -> ModelFile.Bundled(if (root.isEmpty()) relative else "$root/$relative") },
                recognition = recognition,
                synthesis = synthesis,
                threads = threads,
            )
        }

        /**
         * Uses local Whisper tiny INT8 and Kitten nano INT8 files in the sample layout.
         *
         * [directory] contains `whisper/` and `kitten/` folders and must not be blank. Absolute-path
         * and file-existence checks are performed by the platform resolver when the model is used.
         * Filesystem roots and Windows separators are preserved. No files are downloaded or loaded
         * by this preset. Use the model constructors for other compatible exports or filenames.
         */
        public fun local(
            directory: String,
            recognition: Boolean = true,
            synthesis: Boolean = true,
            threads: Int = 2,
        ): SherpaModels {
            require(directory.isNotBlank()) { "A local model directory is required" }
            val separator = if ('\\' in directory && '/' !in directory) '\\' else '/'
            val prefix = if (directory.endsWith('/') || directory.endsWith('\\')) directory else "$directory$separator"
            return preset(
                file = { relative -> ModelFile.Local(prefix + relative.replace('/', separator)) },
                recognition = recognition,
                synthesis = synthesis,
                threads = threads,
            )
        }

        private fun preset(
            file: (String) -> ModelFile,
            recognition: Boolean,
            synthesis: Boolean,
            threads: Int,
        ): SherpaModels = SherpaModels(
            recognition = if (recognition) WhisperModel(
                encoder = file("whisper/tiny-encoder.int8.onnx"),
                decoder = file("whisper/tiny-decoder.int8.onnx"),
                tokens = file("whisper/tiny-tokens.txt"),
                displayName = "Whisper tiny",
            ) else null,
            synthesis = if (synthesis) KittenModel(
                model = file("kitten/model.int8.onnx"),
                voices = file("kitten/voices.bin"),
                tokens = file("kitten/tokens.txt"),
                lexicon = file("kitten/lexicon-en-us.txt"),
                displayName = "Kitten nano · English",
            ) else null,
            threads = threads,
        )
    }
}
