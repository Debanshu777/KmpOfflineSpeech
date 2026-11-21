@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.debanshu.speech

import dev.debanshu.speech.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.posix.memset
import kotlin.coroutines.resume

/** Foreground-only demo. Audio handles live on Main; blocking inference runs on Default. */
class IosSpeechPlatform : SpeechPlatform {
    private val modelMutex = Mutex()
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
        check(!closed) { "Speech engine is closed" }
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

    override suspend fun transcribe(audio: PcmAudio): String = modelMutex.withLock {
        check(!closed) { "Speech engine is closed" }
        require(audio.sampleRate > 0 && audio.samples.size >= audio.sampleRate / 5) { "Record at least 0.2 seconds" }
        require(audio.samples.size.toLong() <= audio.sampleRate.toLong() * 20) { "Record at most 20 seconds" }
        withContext(Dispatchers.Default) {
            memScoped {
                val config = alloc<SherpaOnnxOfflineRecognizerConfig>()
                memset(config.ptr, 0, sizeOf<SherpaOnnxOfflineRecognizerConfig>().convert())
                config.feat_config.sample_rate = 16000
                config.feat_config.feature_dim = 80
                config.model_config.whisper.encoder = model("whisper/tiny-encoder.int8.onnx").cstr.ptr
                config.model_config.whisper.decoder = model("whisper/tiny-decoder.int8.onnx").cstr.ptr
                config.model_config.whisper.language = "en".cstr.ptr
                config.model_config.whisper.task = "transcribe".cstr.ptr
                config.model_config.tokens = model("whisper/tiny-tokens.txt").cstr.ptr
                config.model_config.num_threads = 2
                config.model_config.provider = "cpu".cstr.ptr
                config.decoding_method = "greedy_search".cstr.ptr
                val recognizer = SherpaOnnxCreateOfflineRecognizer(config.ptr)
                    ?: error("Could not load Whisper; run ./gradlew prepareIosSpeech and rebuild")
                try {
                    val stream = SherpaOnnxCreateOfflineStream(recognizer) ?: error("Could not create an audio stream")
                    try {
                        audio.samples.usePinned { pinned ->
                            // Keep the hardware rate; sherpa performs its own band-limited resampling.
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

    override suspend fun synthesize(text: String): PcmAudio = modelMutex.withLock {
        check(!closed) { "Speech engine is closed" }
        require(text.isNotBlank() && text.length <= 500) { "Enter 1–500 characters" }
        withContext(Dispatchers.Default) {
            memScoped {
                val config = alloc<SherpaOnnxOfflineTtsConfig>()
                memset(config.ptr, 0, sizeOf<SherpaOnnxOfflineTtsConfig>().convert())
                config.model.kitten.model = model("kitten/model.int8.onnx").cstr.ptr
                config.model.kitten.voices = model("kitten/voices.bin").cstr.ptr
                config.model.kitten.tokens = model("kitten/tokens.txt").cstr.ptr
                config.model.kitten.lexicon = model("kitten/lexicon-en-us.txt").cstr.ptr
                config.model.kitten.length_scale = 1f
                config.model.num_threads = 2
                config.model.provider = "cpu".cstr.ptr
                config.max_num_sentences = 1
                config.silence_scale = 0.2f
                val tts = SherpaOnnxCreateOfflineTts(config.ptr) ?: error("Could not load KittenTTS")
                try {
                    val generation = alloc<SherpaOnnxGenerationConfig>()
                    memset(generation.ptr, 0, sizeOf<SherpaOnnxGenerationConfig>().convert())
                    generation.sid = 0
                    generation.speed = 1f
                    generation.silence_scale = 0.2f
                    val output = SherpaOnnxOfflineTtsGenerateWithConfig(tts, text.cstr.ptr, generation.ptr, null, null)
                        ?: error("KittenTTS returned no audio")
                    try {
                        currentCoroutineContext().ensureActive()
                        val samples = output.pointed.samples ?: error("KittenTTS returned empty audio")
                        check(output.pointed.n > 0)
                        PcmAudio(FloatArray(output.pointed.n) { samples[it] }, output.pointed.sample_rate)
                    } finally {
                        SherpaOnnxDestroyOfflineTtsGeneratedAudio(output)
                    }
                } finally {
                    SherpaOnnxDestroyOfflineTts(tts)
                }
            }
        }
    }

    override suspend fun play(audio: PcmAudio) = withContext(Dispatchers.Main) {
        check(!closed) { "Speech engine is closed" }
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
        modelMutex.withLock {
            withContext(Dispatchers.Main) {
                closed = true
                stopAudio()
                speech_capture_destroy(capture)
                capture = null
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

    private fun model(relative: String): String {
        val path = "${NSBundle.mainBundle.resourcePath}/models/$relative"
        check(NSFileManager.defaultManager.fileExistsAtPath(path)) { "Missing $relative; run ./gradlew prepareIosSpeech and rebuild" }
        return path
    }
}
