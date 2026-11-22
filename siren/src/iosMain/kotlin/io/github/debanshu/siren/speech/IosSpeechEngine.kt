@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.debanshu.siren.speech

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.internal.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.posix.memset

/** Each configured capability owns its native session only for the duration of a call. */
public fun createIosSpeechEngine(models: SherpaModels = SherpaModels.bundled()): SpeechEngine =
    ModelSpeechEngine(
        recognizer = models.recognition?.let { IosWhisperRecognizer(it, models.threads) },
        synthesizer = models.synthesis?.let { IosKittenSynthesizer(it, models.threads) },
    )

private class IosWhisperRecognizer(
    private val model: WhisperModel,
    private val threads: Int,
) : SpeechRecognizer {
    override val displayName: String = model.displayName

    override suspend fun transcribe(audio: PcmAudio): String {
        require(audio.samples.size >= audio.sampleRate / 5) { "Record at least 0.2 seconds" }
        require(audio.samples.size.toLong() <= audio.sampleRate.toLong() * 20) { "Record at most 20 seconds" }
        return withContext(Dispatchers.Default) {
            memScoped {
                val config = alloc<SherpaOnnxOfflineRecognizerConfig>()
                memset(config.ptr, 0, sizeOf<SherpaOnnxOfflineRecognizerConfig>().convert())
                config.feat_config.sample_rate = 16000
                config.feat_config.feature_dim = 80
                config.model_config.whisper.encoder = model.encoder.resolve().cstr.ptr
                config.model_config.whisper.decoder = model.decoder.resolve().cstr.ptr
                config.model_config.whisper.language = model.language.cstr.ptr
                config.model_config.whisper.task = model.task.cstr.ptr
                config.model_config.tokens = model.tokens.resolve().cstr.ptr
                config.model_config.num_threads = threads
                config.model_config.provider = "cpu".cstr.ptr
                config.decoding_method = "greedy_search".cstr.ptr
                val recognizer = SherpaOnnxCreateOfflineRecognizer(config.ptr)
                    ?: error("Could not load ${model.displayName} from the configured files")
                try {
                    val stream = SherpaOnnxCreateOfflineStream(recognizer) ?: error("Could not create an audio stream")
                    try {
                        audio.samples.usePinned { pinned ->
                            // Preserve the hardware rate; sherpa performs the required resampling.
                            SherpaOnnxAcceptWaveformOffline(stream, audio.sampleRate, pinned.addressOf(0), audio.samples.size)
                        }
                        SherpaOnnxDecodeOfflineStream(recognizer, stream)
                        currentCoroutineContext().ensureActive()
                        val result = SherpaOnnxGetOfflineStreamResult(stream) ?: error("Whisper returned no result")
                        try {
                            result.pointed.text?.toKString()?.trim().orEmpty()
                        } finally {
                            SherpaOnnxDestroyOfflineRecognizerResult(result)
                        }
                    } finally {
                        SherpaOnnxDestroyOfflineStream(stream)
                    }
                } finally {
                    SherpaOnnxDestroyOfflineRecognizer(recognizer)
                }
            }
        }
    }
}

private class IosKittenSynthesizer(
    private val model: KittenModel,
    private val threads: Int,
) : SpeechSynthesizer {
    override val displayName: String = model.displayName

    override suspend fun synthesize(text: String): PcmAudio {
        require(text.isNotBlank()) { "Enter text to synthesize" }
        var generated: PcmAudio? = null
        try {
            return withContext(Dispatchers.Default) {
                memScoped {
                    val config = alloc<SherpaOnnxOfflineTtsConfig>()
                    memset(config.ptr, 0, sizeOf<SherpaOnnxOfflineTtsConfig>().convert())
                    config.model.kitten.model = model.model.resolve().cstr.ptr
                    config.model.kitten.voices = model.voices.resolve().cstr.ptr
                    config.model.kitten.tokens = model.tokens.resolve().cstr.ptr
                    config.model.kitten.lexicon = model.lexicon.resolve().cstr.ptr
                    config.model.kitten.length_scale = 1f
                    config.model.num_threads = threads
                    config.model.provider = "cpu".cstr.ptr
                    config.max_num_sentences = 1
                    config.silence_scale = 0.2f
                    val tts = SherpaOnnxCreateOfflineTts(config.ptr)
                        ?: error("Could not load ${model.displayName} from the configured files")
                    try {
                        require(model.speakerId < SherpaOnnxOfflineTtsNumSpeakers(tts)) {
                            "Configured speaker ${model.speakerId} is unavailable in ${model.displayName}"
                        }
                        val generation = alloc<SherpaOnnxGenerationConfig>()
                        memset(generation.ptr, 0, sizeOf<SherpaOnnxGenerationConfig>().convert())
                        generation.sid = model.speakerId
                        generation.speed = model.speed
                        generation.silence_scale = 0.2f
                        val output = SherpaOnnxOfflineTtsGenerateWithConfig(tts, text.cstr.ptr, generation.ptr, null, null)
                            ?: error("KittenTTS returned no audio")
                        try {
                            currentCoroutineContext().ensureActive()
                            val samples = output.pointed.samples ?: error("KittenTTS returned empty audio")
                            check(output.pointed.n > 0)
                            PcmAudio(FloatArray(output.pointed.n) { samples[it] }, output.pointed.sample_rate)
                                .also { generated = it }
                        } finally {
                            SherpaOnnxDestroyOfflineTtsGeneratedAudio(output)
                        }
                    } finally {
                        SherpaOnnxDestroyOfflineTts(tts)
                    }
                }
            }
        } catch (error: Throwable) {
            // Also cover prompt cancellation while returning from the worker dispatcher.
            generated?.samples?.fill(0f)
            throw error
        }
    }
}

private fun ModelFile.resolve(): String {
    require('\u0000' !in path) { "A model path cannot contain a null character" }
    val resolved = when (this) {
        is ModelFile.Bundled -> "${checkNotNull(NSBundle.mainBundle.resourcePath) { "App bundle resources are unavailable" }}/$path"
        is ModelFile.Local -> {
            require(path.startsWith('/')) { "A local model path must be absolute: $path" }
            path
        }
    }
    val manager = NSFileManager.defaultManager
    memScoped {
        val directory = alloc<BooleanVar>()
        require(manager.fileExistsAtPath(resolved, isDirectory = directory.ptr) && !directory.value &&
            manager.isReadableFileAtPath(resolved)) { "Missing or unreadable model file: $resolved" }
    }
    val size = (manager.attributesOfItemAtPath(resolved, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    require(size > 0L) { "A model file must not be empty: $resolved" }
    return resolved
}

/** Create an engine for the standard app-owned model bundle, or supply a custom configuration. */
public fun createSpeechEngine(
    models: SherpaModels = SherpaModels.bundled(),
): SpeechEngine = createIosSpeechEngine(models)
