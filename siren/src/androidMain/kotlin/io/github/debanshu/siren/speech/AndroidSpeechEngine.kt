package io.github.debanshu.siren.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.debanshu.siren.audio.PcmAudio
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Build only the configured capabilities; model files are resolved lazily on first use. */
public fun createAndroidSpeechEngine(
    context: Context,
    models: SherpaModels = SherpaModels.bundled(),
): SpeechEngine {
    val files = AndroidModelFiles(context.applicationContext)
    return ModelSpeechEngine(
        recognizer = models.recognition?.let { AndroidWhisperRecognizer(it, models.threads, files) },
        synthesizer = models.synthesis?.let { AndroidKittenSynthesizer(it, models.threads, files) },
    )
}

private class AndroidWhisperRecognizer(
    private val model: WhisperModel,
    private val threads: Int,
    private val files: AndroidModelFiles,
) : SpeechRecognizer {
    override val displayName: String = model.displayName

    override suspend fun transcribe(audio: PcmAudio): String = withContext(Dispatchers.Default) {
        require(audio.samples.size.toLong() >= audio.sampleRate.toLong() / 5 &&
            audio.samples.size.toLong() <= audio.sampleRate.toLong() * 20
        ) { "This Whisper adapter accepts between 0.2 and 20 seconds of audio." }
        val encoder = files.resolve(model.encoder)
        val decoder = files.resolve(model.decoder)
        val tokens = files.resolve(model.tokens)
        currentCoroutineContext().ensureActive()
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder,
                        decoder = decoder,
                        language = model.language,
                        task = model.task,
                    ),
                    tokens = tokens,
                    numThreads = threads,
                    provider = "cpu",
                ),
            ),
        )
        try {
            currentCoroutineContext().ensureActive()
            val stream = recognizer.createStream()
            try {
                // Keep the actual input rate. sherpa resamples it for the model.
                stream.acceptWaveform(audio.samples, audio.sampleRate)
                recognizer.decode(stream)
                currentCoroutineContext().ensureActive()
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }
}

private class AndroidKittenSynthesizer(
    private val model: KittenModel,
    private val threads: Int,
    private val files: AndroidModelFiles,
) : SpeechSynthesizer {
    override val displayName: String = model.displayName

    override suspend fun synthesize(text: String): PcmAudio {
        var generatedSamples: FloatArray? = null
        return try {
            withContext(Dispatchers.Default) {
                require(text.isNotBlank()) { "Enter text to synthesize" }
                val modelPath = files.resolve(model.model)
                val voices = files.resolve(model.voices)
                val tokens = files.resolve(model.tokens)
                val lexicon = files.resolve(model.lexicon)
                currentCoroutineContext().ensureActive()
                val tts = OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kitten = OfflineTtsKittenModelConfig(
                                model = modelPath,
                                voices = voices,
                                tokens = tokens,
                                lexicon = lexicon,
                            ),
                            numThreads = threads,
                            provider = "cpu",
                        ),
                        maxNumSentences = 1,
                    ),
                )
                try {
                    require(model.speakerId < tts.numSpeakers()) { "The configured speaker ID is unavailable in this voice model." }
                    currentCoroutineContext().ensureActive()
                    val audio = tts.generate(text, sid = model.speakerId, speed = model.speed)
                    generatedSamples = audio.samples
                    currentCoroutineContext().ensureActive()
                    PcmAudio(audio.samples, audio.sampleRate)
                } finally {
                    tts.release()
                }
            }
        } catch (error: Throwable) {
            // Also cover prompt cancellation while returning from the worker dispatcher.
            generatedSamples?.fill(0f)
            throw error
        }
    }
}

/** Native APIs need ordinary file paths; packaged assets are materialized in private storage. */
private class AndroidModelFiles(private val context: Context) {
    private val bundledRoot: File by lazy {
        // An app update can replace an asset at the same path. Do not reuse the previous APK's copy.
        val updated = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        File(context.noBackupFilesDir, "siren-models/$updated").canonicalFile
    }

    public suspend fun resolve(model: ModelFile): String = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        when (model) {
            is ModelFile.Local -> {
                val file = File(model.path)
                require(file.isAbsolute) { "A local model file must use an absolute path." }
                requireReadable(file)
                file.canonicalPath
            }
            is ModelFile.Bundled -> bundled(model)
        }
    }

    private suspend fun bundled(model: ModelFile.Bundled): String {
        val root = bundledRoot
        check(root.mkdirs() || root.isDirectory) { "Could not create the private model directory." }
        val candidate = File(root, model.path)
        check(!Files.isSymbolicLink(candidate.toPath())) { "A bundled model destination cannot be a symbolic link." }
        val target = candidate.canonicalFile
        check(target.toPath().startsWith(root.toPath()) && target != root) { "Bundled model path escapes private storage." }
        if (target.isFile && target.canRead() && target.length() > 0) return target.absolutePath
        val parent = checkNotNull(target.parentFile)
        check(parent.mkdirs() || parent.isDirectory) { "Could not create the model's private directory." }
        val temporary = File.createTempFile("siren-", ".part", parent)
        try {
            context.assets.open(model.path).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            requireReadable(temporary)
            currentCoroutineContext().ensureActive()
            try {
                Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), REPLACE_EXISTING)
            }
            requireReadable(target)
            return target.absolutePath
        } finally {
            temporary.delete()
        }
    }

    private fun requireReadable(file: File) {
        require(file.isFile && file.canRead() && file.length() > 0) { "Missing, empty, or unreadable model file: ${file.name}" }
    }
}

/** Create an engine for the standard app-owned model bundle, or supply a custom configuration. */
public fun createSpeechEngine(
    context: Context,
    models: SherpaModels = SherpaModels.bundled(),
): SpeechEngine = createAndroidSpeechEngine(context, models)
