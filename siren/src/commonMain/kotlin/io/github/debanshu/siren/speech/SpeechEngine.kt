package io.github.debanshu.siren.speech

import io.github.debanshu.siren.audio.PcmAudio
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public data class SpeechCapabilities(
    public val recognition: Boolean,
    public val synthesis: Boolean,
    public val recognitionLabel: String? = null,
    public val synthesisLabel: String? = null,
)

/** Implement for another recognition model without changing recording or UI code. */
public interface SpeechRecognizer {
    public val displayName: String get() = "Recognition model"
    public suspend fun transcribe(audio: PcmAudio): String
    public suspend fun close(): Unit {}
}

/** Implement for another synthesis model; preserve the generated audio's sample rate. */
public interface SpeechSynthesizer {
    public val displayName: String get() = "Synthesis model"
    public suspend fun synthesize(text: String): PcmAudio
    public suspend fun close(): Unit {}
}

public interface SpeechEngine {
    public val capabilities: SpeechCapabilities
    public suspend fun transcribe(audio: PcmAudio): String
    public suspend fun synthesize(text: String): PcmAudio
    public suspend fun close(): Unit
}

/** One immutable set of optional models. Close this engine before replacing its providers. */
public class ModelSpeechEngine(
    private val recognizer: SpeechRecognizer? = null,
    private val synthesizer: SpeechSynthesizer? = null,
) : SpeechEngine {
    public override val capabilities: SpeechCapabilities = SpeechCapabilities(
        recognizer != null, synthesizer != null, recognizer?.displayName, synthesizer?.displayName,
    )
    private val ownership = Mutex()
    private var closed = false

    override suspend fun transcribe(audio: PcmAudio): String = ownership.withLock {
        check(!closed) { "Speech engine is closed" }
        val model = recognizer ?: throw UnsupportedOperationException("No recognition model is configured")
        model.transcribe(audio).also { currentCoroutineContext().ensureActive() }
    }

    override suspend fun synthesize(text: String): PcmAudio = ownership.withLock {
        check(!closed) { "Speech engine is closed" }
        require(text.isNotBlank()) { "Enter text to synthesize" }
        val model = synthesizer ?: throw UnsupportedOperationException("No synthesis model is configured")
        val audio = model.synthesize(text)
        try {
            currentCoroutineContext().ensureActive()
            audio
        } catch (error: Throwable) {
            audio.samples.fill(0f)
            throw error
        }
    }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        ownership.withLock {
            if (closed) return@withLock
            closed = true
            try { recognizer?.close() } finally { synthesizer?.close() }
        }
    }
}
