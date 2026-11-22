@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.debanshu.siren.sample.platform

import io.github.debanshu.siren.sample.SampleModels

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.sample.internal.audio.*
import io.github.debanshu.siren.sample.SpeechPlatform
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createIosSpeechEngine
import kotlinx.cinterop.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Foreground demo audio; model ownership and inference belong to the injected engine. */
class IosSpeechPlatform(
    private val engine: SpeechEngine = createIosSpeechEngine(SampleModels.bundledDemo()),
) : SpeechPlatform {
    override val capabilities get() = engine.capabilities
    private var capture: COpaquePointer? = speech_capture_create()
    private var player: COpaquePointer? = null
    private var closed = false

    override suspend fun requestMicrophonePermission(): Boolean = suspendCancellableCoroutine { continuation ->
        val reference = StableRef.create(continuation)
        speech_request_permission(staticCFunction { granted: Int, context: COpaquePointer? ->
            val callback = context!!.asStableRef<CancellableContinuation<Boolean>>()
            val waiting = callback.get()
            callback.dispose()
            if (waiting.isActive) waiting.resume(granted != 0)
        }, reference.asCPointer())
    }

    override suspend fun startRecording() = withContext(Dispatchers.Main) {
        check(!closed) { "Speech platform is closed" }
        check(capabilities.recognition) { "No recognition model is configured" }
        stopAudio()
        check(speech_capture_start(capture) == 1) { "Could not start the microphone" }
    }

    override suspend fun stopRecording(): PcmAudio = withContext(Dispatchers.Main) {
        memScoped {
            val count = alloc<IntVar>()
            val rate = alloc<IntVar>()
            val samples = speech_capture_stop(capture, count.ptr, rate.ptr)
            try {
                PcmAudio(if (samples == null) FloatArray(0) else FloatArray(count.value) { samples[it] }, rate.value)
            } finally {
                speech_free_samples(samples)
                speech_audio_deactivate()
            }
        }
    }

    override suspend fun transcribe(audio: PcmAudio): String = engine.transcribe(audio)

    override suspend fun synthesize(text: String): PcmAudio = engine.synthesize(text)

    override suspend fun play(audio: PcmAudio) = withContext(Dispatchers.Main) {
        check(!closed) { "Speech platform is closed" }
        require(audio.samples.isNotEmpty()) { "There is no audio to play" }
        stopAudio()
        val started = audio.samples.usePinned {
            speech_player_start(it.addressOf(0), audio.samples.size, audio.sampleRate)
        } ?: error("Could not play speech")
        player = started
        try {
            while (player == started && speech_player_is_playing(started) != 0) delay(100)
        } finally {
            if (player == started) stopAudio()
        }
    }

    override suspend fun stop() = withContext(NonCancellable + Dispatchers.Main) { stopAudio() }

    override suspend fun close() = withContext(NonCancellable) {
        withContext(Dispatchers.Main) { closed = true }
        try {
            // The engine waits for synchronous model work before closing its providers.
            engine.close()
        } finally {
            withContext(Dispatchers.Main) {
                try {
                    stopAudio()
                } finally {
                    speech_capture_destroy(capture)
                    capture = null
                }
            }
        }
    }

    private fun stopAudio() {
        speech_player_destroy(player)
        player = null
        if (capture != null) memScoped {
            val count = alloc<IntVar>()
            val rate = alloc<IntVar>()
            speech_free_samples(speech_capture_stop(capture, count.ptr, rate.ptr))
        }
        speech_audio_deactivate()
    }
}
