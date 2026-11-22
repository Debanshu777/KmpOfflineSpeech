package io.github.debanshu.siren.sample.platform

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.sample.SampleModels
import io.github.debanshu.siren.sample.SpeechPlatform
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createJvmSpeechEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The desktop sample owns JavaSound only; the injected SDK engine owns inference. */
class JvmSpeechPlatform(
    private val engine: SpeechEngine = createJvmSpeechEngine(SampleModels.bundledDemo()),
) : SpeechPlatform {
    override val capabilities get() = engine.capabilities
    private val control = Mutex()
    private val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Capture? = null
    private var playback: Playback? = null
    private var closed = false

    // JavaSound has no portable permission preflight. The OS checks when the line opens.
    override suspend fun requestMicrophonePermission(): Boolean = true

    override suspend fun startRecording(): Unit = withContext(Dispatchers.IO) {
        control.withLock {
            check(!closed) { "Speech platform is closed" }
            check(capabilities.recognition) { "No recognition model is configured" }
            stopAudio()
            val line = openMicrophone()
            val state = Capture(line, line.format.sampleRate.toInt())
            capture = state
            try {
                line.start()
                state.reader = workers.launch { readMicrophone(state) }
            } catch (error: Throwable) {
                capture = null
                line.close()
                throw error
            }
        }
    }

    override suspend fun stopRecording(): PcmAudio = withContext(NonCancellable + Dispatchers.IO) {
        control.withLock {
            val state = capture ?: error("No recording is active")
            capture = null
            finishCapture(state)
            try {
                state.failure?.let { throw IllegalStateException("Microphone capture failed", it) }
                PcmAudio(state.samples.copyOf(state.count), state.rate)
            } finally {
                state.samples.fill(0f)
            }
        }
    }

    override suspend fun transcribe(audio: PcmAudio): String = engine.transcribe(audio)
    override suspend fun synthesize(text: String): PcmAudio = engine.synthesize(text)

    override suspend fun play(audio: PcmAudio) {
        val state = control.withLock {
            check(!closed) { "Speech platform is closed" }
            stopAudio()
            Playback().also { playback = it }
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val worker = workers.launch {
                    var failure: Throwable? = null
                    try {
                        val format = format(audio.sampleRate)
                        val line = AudioSystem.getSourceDataLine(format)
                        state.line.set(line)
                        currentCoroutineContext().ensureActive()
                        if (!state.stopped.get() && continuation.isActive) {
                            line.open(format)
                            line.start()
                            val bytes = ByteArray(4096)
                            try {
                                var offset = 0
                                while (offset < audio.samples.size && !state.stopped.get()) {
                                    currentCoroutineContext().ensureActive()
                                    val count = minOf(bytes.size / 2, audio.samples.size - offset)
                                    repeat(count) { index ->
                                        val pcm = (audio.samples[offset + index].coerceIn(-1f, 1f) * 32767f).toInt()
                                        bytes[index * 2] = pcm.toByte()
                                        bytes[index * 2 + 1] = (pcm shr 8).toByte()
                                    }
                                    var written = 0
                                    while (written < count * 2 && !state.stopped.get()) {
                                        val n = line.write(bytes, written, count * 2 - written)
                                        check(n > 0 || state.stopped.get()) { "Audio output stopped accepting samples" }
                                        written += n
                                    }
                                    offset += count
                                }
                                if (!state.stopped.get()) line.drain()
                            } finally { bytes.fill(0) }
                        }
                    } catch (error: Throwable) {
                        if (!state.stopped.get()) failure = error
                    } finally {
                        closePlayback(state)
                    }
                    if (continuation.isActive) {
                        failure?.let { continuation.resumeWithException(it) } ?: continuation.resume(Unit)
                    }
                }
                state.worker = worker
                worker.invokeOnCompletion { error ->
                    // A concurrently closed scope can cancel this worker before its body starts.
                    if (error != null && continuation.isActive) continuation.resumeWithException(error)
                }
                continuation.invokeOnCancellation {
                    state.stopped.set(true)
                    closePlayback(state)
                    worker.cancel()
                }
            }
        } finally {
            withContext(NonCancellable) {
                control.withLock {
                    closePlayback(state)
                    state.worker?.join()
                    if (playback === state) playback = null
                }
            }
        }
    }

    override suspend fun stop(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        control.withLock { stopAudio() }
    }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        control.withLock {
            closed = true
            stopAudio()
        }
        try { engine.close() } finally {
            workers.cancel()
            workers.coroutineContext.job.join()
        }
    }

    private suspend fun stopAudio() {
        capture?.let { state ->
            capture = null
            finishCapture(state)
            state.samples.fill(0f)
        }
        playback?.let { state ->
            playback = null
            closePlayback(state)
            state.worker?.join()
        }
    }

    private suspend fun finishCapture(state: Capture) {
        state.reader?.cancel()
        runCatching { state.line.stop() }
        runCatching { state.line.close() } // Unblocks a pending JavaSound read.
        state.reader?.join()
    }

    private fun closePlayback(state: Playback) {
        state.stopped.set(true)
        state.line.getAndSet(null)?.let { line ->
            runCatching { line.stop() }
            runCatching { line.flush() }
            runCatching { line.close() } // Also unblocks write/drain on cancellation.
        }
    }

    private suspend fun readMicrophone(state: Capture) {
        val bytes = ByteArray(4096)
        try {
            while (currentCoroutineContext().isActive && state.count < state.samples.size) {
                val wanted = minOf(bytes.size, (state.samples.size - state.count) * 2)
                val count = state.line.read(bytes, 0, wanted)
                if (count <= 0) break
                repeat(count / 2) { index ->
                    val pcm = ((bytes[index * 2].toInt() and 0xff) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
                    state.samples[state.count++] = pcm / 32768f
                }
            }
        } catch (error: Exception) {
            if (currentCoroutineContext().isActive) state.failure = error
        } finally {
            bytes.fill(0)
            runCatching { state.line.stop() }
            runCatching { state.line.close() }
        }
    }

    private fun openMicrophone(): TargetDataLine {
        var failure: Exception? = null
        for (rate in listOf(16_000, 48_000, 44_100)) {
            val format = format(rate)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) continue
            var line: TargetDataLine? = null
            try {
                line = AudioSystem.getLine(info) as TargetDataLine
                line.open(format)
                check(line.format.sampleRate == rate.toFloat() && line.format.channels == 1 &&
                    line.format.sampleSizeInBits == 16 && !line.format.isBigEndian) { "Unexpected microphone PCM format" }
                return line
            } catch (error: SecurityException) {
                line?.close()
                throw IllegalStateException("Allow microphone access for Siren in your operating system's privacy settings", error)
            } catch (error: Exception) {
                line?.close()
                failure = error
            }
        }
        throw IllegalStateException("No compatible microphone is available. Check the input device and microphone permission.", failure)
    }

    private fun format(rate: Int) = AudioFormat(rate.toFloat(), 16, 1, true, false)
    private class Capture(val line: TargetDataLine, val rate: Int) {
        val samples = FloatArray(rate * 20)
        var count = 0
        var reader: Job? = null
        var failure: Exception? = null
    }
    private class Playback {
        val stopped = AtomicBoolean(false)
        val line = AtomicReference<SourceDataLine?>()
        var worker: Job? = null
    }
}
