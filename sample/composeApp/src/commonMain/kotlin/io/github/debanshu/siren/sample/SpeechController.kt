package io.github.debanshu.siren.sample

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.debanshu.siren.speech.SpeechCapabilities

enum class Phase { Idle, Starting, Recording, Transcribing, Synthesizing, Playing, Stopping }
data class SpeechUiState(
    val phase: Phase = Phase.Idle,
    val text: String = "Hello from Kotlin Multiplatform. This voice runs on your device.",
    val error: String? = null,
    val capabilities: SpeechCapabilities = SpeechCapabilities(true, true),
)

/** Call actions on the UI thread. A mutex protects native ownership across cancellation. */
class SpeechController(private val platform: SpeechPlatform, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(SpeechUiState(capabilities = platform.capabilities))
    val state = mutableState.asStateFlow()
    private val ownership = Mutex()
    private var operation: Job? = null
    private var recordingLimit: Job? = null
    private var closed = false

    fun edit(text: String) {
        if (state.value.phase == Phase.Idle) mutableState.value = state.value.copy(text = text.take(500), error = null)
    }

    fun record() {
        if (closed || state.value.phase != Phase.Idle) return
        if (!state.value.capabilities.recognition) {
            mutableState.value = state.value.copy(error = "No recognition model is configured")
            return
        }
        mutableState.value = state.value.copy(phase = Phase.Starting, error = null)
        runOperation {
            check(platform.requestMicrophonePermission()) { "Microphone permission was denied. Enable it in Settings to record." }
            platform.startRecording()
            mutableState.value = state.value.copy(phase = Phase.Recording)
            recordingLimit = scope.launch { delay(20_000); transcribe() }
        }
    }

    fun transcribe() {
        if (closed || state.value.phase != Phase.Recording) return
        recordingLimit?.cancel()
        mutableState.value = state.value.copy(phase = Phase.Transcribing, error = null)
        runOperation {
            val audio = platform.stopRecording()
            try {
                require(audio.samples.size >= audio.sampleRate / 5) { "Record at least 0.2 seconds of speech." }
                val text = platform.transcribe(audio).trim()
                require(text.isNotEmpty()) { "No words were recognized. Try a clearer recording." }
                mutableState.value = state.value.copy(text = text, phase = Phase.Idle)
            } finally {
                audio.samples.fill(0f)
            }
        }
    }

    fun speak() {
        if (closed || state.value.phase != Phase.Idle || state.value.text.isBlank()) return
        if (!state.value.capabilities.synthesis) {
            mutableState.value = state.value.copy(error = "No synthesis model is configured")
            return
        }
        val text = state.value.text.take(500)
        mutableState.value = state.value.copy(phase = Phase.Synthesizing, error = null)
        runOperation {
            val audio = platform.synthesize(text)
            try {
                mutableState.value = state.value.copy(phase = Phase.Playing)
                platform.play(audio)
                mutableState.value = state.value.copy(phase = Phase.Idle)
            } finally {
                audio.samples.fill(0f)
            }
        }
    }

    fun stop() {
        if (closed) return
        recordingLimit?.cancel()
        operation?.cancel()
        mutableState.value = state.value.copy(phase = Phase.Stopping)
        operation = scope.launch {
            ownership.withLock {
                try { platform.stop() } finally {
                    mutableState.value = state.value.copy(phase = Phase.Idle)
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        recordingLimit?.cancel()
        operation?.cancel()
        scope.launch {
            ownership.withLock {
                try { platform.close() } finally { scope.cancel() }
            }
        }
    }

    private fun runOperation(block: suspend () -> Unit) {
        operation = scope.launch {
            ownership.withLock {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.value = state.value.copy(phase = Phase.Idle, error = error.message ?: "Speech failed")
                } finally {
                    // Cancellation waits for synchronous native work to return before cleanup.
                    if (!isActive || state.value.error != null) {
                        withContext(NonCancellable) { platform.stop() }
                    }
                }
            }
        }
    }
}
