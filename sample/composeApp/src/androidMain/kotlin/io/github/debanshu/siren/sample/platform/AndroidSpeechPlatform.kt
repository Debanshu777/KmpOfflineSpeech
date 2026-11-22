package io.github.debanshu.siren.sample.platform

import io.github.debanshu.siren.sample.SampleModels

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.sample.SpeechPlatform
import io.github.debanshu.siren.speech.SpeechCapabilities
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Create in Activity.onCreate, before the Activity reaches STARTED. */
fun createAndroidSpeechPlatform(
    activity: ComponentActivity,
    engine: SpeechEngine = createAndroidSpeechEngine(activity.applicationContext, SampleModels.bundledDemo()),
): SpeechPlatform = AndroidSpeechPlatform(activity, engine)

private class AndroidSpeechPlatform(
    activity: ComponentActivity,
    private val engine: SpeechEngine,
) : SpeechPlatform {
    override val capabilities: SpeechCapabilities get() = engine.capabilities
    private val context = activity.applicationContext
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionResult?.complete(granted)
        permissionResult = null
    }
    private val audioLock = Any()
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var capture: Capture? = null
    private var player: AudioTrack? = null
    private val playbackGeneration = AtomicLong(0)
    private val closeOwnership = Mutex()
    private var closed = false

    override suspend fun requestMicrophonePermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        check(!closed) { "Speech platform is closed" }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return@withContext true
        }
        val existing = permissionResult
        val result = existing ?: CompletableDeferred<Boolean>().also {
            permissionResult = it
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        result.await()
    }

    override suspend fun startRecording() = withContext(Dispatchers.IO) {
        check(!closed) { "Speech platform is closed" }
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required. Enable it in Android Settings if previously denied."
        }
        check(synchronized(audioLock) { capture == null }) { "Already recording" }
        val recorder = openRecorder()
        val active = Capture(recorder, recorder.sampleRate)
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "The microphone could not start. Another app may be using it."
            }
            synchronized(audioLock) { capture = active }
            active.job = captureScope.launch { readMicrophone(active) }
        } catch (error: Throwable) {
            synchronized(audioLock) { if (capture === active) capture = null }
            recorder.release()
            throw error
        }
    }

    override suspend fun stopRecording(): PcmAudio = withContext(Dispatchers.IO + NonCancellable) {
        val active = synchronized(audioLock) { capture.also { capture = null } }
            ?: error("There is no active recording")
        finishCapture(active)
        try {
            active.failure?.let { error(it) }
            check(active.count >= active.sampleRate / 5) { "Record at least 0.2 seconds of speech." }
            PcmAudio(active.samples.copyOf(active.count), active.sampleRate)
        } finally {
            active.samples.fill(0f)
        }
    }

    override suspend fun transcribe(audio: PcmAudio): String {
        check(!closed) { "Speech platform is closed" }
        return engine.transcribe(audio)
    }

    override suspend fun synthesize(text: String): PcmAudio {
        check(!closed) { "Speech platform is closed" }
        return engine.synthesize(text)
    }

    override suspend fun play(audio: PcmAudio) = withContext(Dispatchers.IO) {
        check(!closed) { "Speech platform is closed" }
        require(audio.samples.isNotEmpty()) { "The voice returned no audio" }
        val generation = playbackGeneration.incrementAndGet()
        val minimum = AudioTrack.getMinBufferSize(
            audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        check(minimum > 0) { "This device cannot play the generated sample rate." }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minimum, audio.sampleRate * Float.SIZE_BYTES / 5))
            .build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Audio playback could not initialize." }
            synchronized(audioLock) { player = track }
            if (generation != playbackGeneration.get()) return@withContext
            track.play()
            var written = 0
            while (written < audio.samples.size && generation == playbackGeneration.get()) {
                currentCoroutineContext().ensureActive()
                val count = track.write(
                    audio.samples, written, minOf(2_048, audio.samples.size - written),
                    AudioTrack.WRITE_NON_BLOCKING,
                )
                check(count >= 0) { "Audio playback failed ($count)." }
                written += count
                if (count == 0) delay(10)
            }
            // A successful write only queues PCM. Wait until the speaker has consumed it.
            while (generation == playbackGeneration.get() && track.playbackHeadPosition < written) {
                currentCoroutineContext().ensureActive()
                delay(20)
            }
        } finally {
            synchronized(audioLock) {
                if (player === track) player = null
                try { track.stop() } catch (_: IllegalStateException) { /* Already stopped. */ }
                track.release()
            }
        }
    }

    override suspend fun stop() {
        playbackGeneration.incrementAndGet()
        synchronized(audioLock) {
            player?.let {
                try { it.pause(); it.flush() } catch (_: IllegalStateException) { /* Already stopped. */ }
            }
        }
        withContext(Dispatchers.IO + NonCancellable) {
            val active = synchronized(audioLock) { capture.also { capture = null } }
            if (active != null) {
                finishCapture(active)
                active.samples.fill(0f)
            }
        }
    }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        closeOwnership.withLock {
            if (closed) return@withLock
            closed = true
            try {
                stop()
            } finally {
                captureScope.cancel()
                try {
                    withContext(Dispatchers.Main.immediate) {
                        permissionResult?.cancel()
                        permissionResult = null
                        permissionLauncher.unregister()
                    }
                } finally {
                    // The engine waits for any synchronous native call before releasing its providers.
                    engine.close()
                }
            }
        }
    }

    private fun openRecorder(): AudioRecord {
        // 16 kHz is convenient, but not guaranteed by every Android input route.
        for (rate in listOf(16_000, 48_000, 44_100)) {
            val minimum = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minimum <= 0) continue
            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    max(minimum, rate / 5 * Short.SIZE_BYTES),
                )
            } catch (_: IllegalArgumentException) { continue }
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            recorder.release()
        }
        error("This device has no supported mono PCM16 microphone input.")
    }

    private suspend fun readMicrophone(active: Capture) {
        val pcm16 = ShortArray(2_048)
        try {
            while (active.running.get() && active.count < active.samples.size) {
                currentCoroutineContext().ensureActive()
                val count = active.recorder.read(
                    pcm16, 0, minOf(pcm16.size, active.samples.size - active.count),
                    AudioRecord.READ_NON_BLOCKING,
                )
                if (count < 0) {
                    if (active.running.get()) active.failure = "Microphone read failed ($count)."
                    break
                }
                for (index in 0 until count) active.samples[active.count++] = pcm16[index] / 32768f
                if (count == 0) delay(10)
            }
        } catch (error: Exception) {
            if (active.running.get()) active.failure = error.message ?: "Microphone recording failed."
        } finally {
            pcm16.fill(0)
        }
    }

    private suspend fun finishCapture(active: Capture) {
        active.running.set(false)
        try {
            active.recorder.stop()
        } catch (_: IllegalStateException) {
            // A disconnected microphone may already have stopped.
        }
        active.job?.join()
        active.recorder.release()
    }

    private class Capture(val recorder: AudioRecord, val sampleRate: Int) {
        val running = AtomicBoolean(true)
        val samples = FloatArray(sampleRate * 20)
        var count = 0
        var failure: String? = null
        var job: Job? = null
    }

}
