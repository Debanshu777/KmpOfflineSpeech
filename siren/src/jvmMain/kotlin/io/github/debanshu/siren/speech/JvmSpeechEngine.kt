package io.github.debanshu.siren.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.debanshu.siren.audio.PcmAudio
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * CPU speech inference with packaged JVM natives. Bundled files are classpath resources;
 * Local files must be absolute. The engine owns only its materialized resource copies.
 */
public fun createJvmSpeechEngine(
    models: SherpaModels,
    classLoader: ClassLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader(),
): SpeechEngine {
    val files = JvmModelFiles(classLoader)
    val engine = ModelSpeechEngine(
        recognizer = models.recognition?.let { JvmWhisperRecognizer(it, models.threads, files) },
        synthesizer = models.synthesis?.let { JvmKittenSynthesizer(it, models.threads, files) },
    )
    return JvmOwnedSpeechEngine(engine, files)
}

private class JvmOwnedSpeechEngine(
    private val engine: SpeechEngine,
    private val files: JvmModelFiles,
) : SpeechEngine by engine {
    override suspend fun close(): Unit = withContext(NonCancellable) {
        try {
            engine.close()
        } finally {
            withContext(Dispatchers.IO) { files.close() }
        }
    }
}

private class JvmWhisperRecognizer(
    private val model: WhisperModel,
    private val threads: Int,
    private val files: JvmModelFiles,
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
        val config = OfflineRecognizerConfig.builder()
            .setFeatureConfig(FeatureConfig.builder().setSampleRate(16_000).setFeatureDim(80).build())
            .setOfflineModelConfig(
                OfflineModelConfig.builder()
                    .setWhisper(
                        OfflineWhisperModelConfig.builder()
                            .setEncoder(encoder).setDecoder(decoder)
                            .setLanguage(model.language).setTask(model.task).build(),
                    )
                    .setTokens(tokens).setNumThreads(threads).setProvider("cpu").setDebug(false).build(),
            )
            .setDecodingMethod("greedy_search")
            .build()
        val recognizer = OfflineRecognizer(config)
        try {
            currentCoroutineContext().ensureActive()
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(audio.samples, audio.sampleRate)
                recognizer.decode(stream)
                currentCoroutineContext().ensureActive()
                recognizer.getResult(stream).text.orEmpty().trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }
}

private class JvmKittenSynthesizer(
    private val model: KittenModel,
    private val threads: Int,
    private val files: JvmModelFiles,
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
                    OfflineTtsConfig.builder()
                        .setModel(
                            OfflineTtsModelConfig.builder()
                                .setKitten(
                                    OfflineTtsKittenModelConfig.builder()
                                        .setModel(modelPath).setVoices(voices)
                                        .setTokens(tokens).setLexicon(lexicon).build(),
                                )
                                .setNumThreads(threads).setProvider("cpu").setDebug(false).build(),
                        )
                        .setMaxNumSentences(1).build(),
                )
                try {
                    require(model.speakerId < tts.numSpeakers) { "The configured speaker ID is unavailable in this voice model." }
                    currentCoroutineContext().ensureActive()
                    val audio = tts.generate(text, model.speakerId, model.speed)
                    generatedSamples = audio.samples
                    currentCoroutineContext().ensureActive()
                    PcmAudio(audio.samples, audio.sampleRate)
                } finally {
                    tts.release()
                }
            }
        } catch (error: Throwable) {
            generatedSamples?.fill(0f)
            throw error
        }
    }
}

private class JvmModelFiles(private val classLoader: ClassLoader) {
    private var directory: Path? = null
    private val installed = mutableMapOf<String, Path>()

    suspend fun resolve(model: ModelFile): String = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        when (model) {
            is ModelFile.Local -> {
                val file = Path.of(model.path)
                require(file.isAbsolute) { "A local model file must use an absolute path." }
                requireReadable(file)
                file.toRealPath().toString()
            }
            is ModelFile.Bundled -> bundled(model)
        }
    }

    private suspend fun bundled(model: ModelFile.Bundled): String {
        installed[model.path]?.let { if (Files.isRegularFile(it) && Files.size(it) > 0) return it.toString() }
        val root = directory ?: Files.createTempDirectory("siren-jvm-models-").also {
            directory = it
            it.toFile().deleteOnExit()
        }
        val target = root.resolve(model.path).normalize()
        check(target.startsWith(root) && target != root) { "Bundled model path escapes private temporary storage." }
        Files.createDirectories(target.parent)
        val parents = mutableListOf<Path>()
        var parent = target.parent
        while (parent != root) { parents.add(parent); parent = checkNotNull(parent.parent) }
        parents.asReversed().forEach { it.toFile().deleteOnExit() }
        val temporary = Files.createTempFile(target.parent, "siren-", ".part")
        try {
            val resource = classLoader.getResourceAsStream(model.path)
                ?: error("Missing bundled model resource: ${model.path}")
            resource.use { input ->
                Files.newOutputStream(temporary).use { output ->
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
            try { Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, REPLACE_EXISTING) }
            target.toFile().deleteOnExit()
            installed[model.path] = target
            return target.toString()
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireReadable(file: Path) {
        require(Files.isRegularFile(file) && Files.isReadable(file) && Files.size(file) > 0) {
            "Missing, empty, or unreadable model file: ${file.fileName}"
        }
    }

    @Synchronized
    fun close() {
        directory?.toFile()?.deleteRecursively()
        directory = null
        installed.clear()
    }
}
