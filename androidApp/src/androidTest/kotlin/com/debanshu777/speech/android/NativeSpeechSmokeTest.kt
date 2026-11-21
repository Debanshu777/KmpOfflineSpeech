package com.debanshu777.speech.android

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Exercises the packaged JNI library and both real models without recording a person. */
@RunWith(AndroidJUnit4::class)
class NativeSpeechSmokeTest {
    @Test(timeout = 120_000)
    fun kittenAudioCanBeTranscribedByWhisper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "speech-smoke-${System.nanoTime()}")
        check(directory.mkdirs())
        var samples: FloatArray? = null
        try {
            copyModelAssets(context, directory)
            val audio = synthesize(directory)
            samples = audio.samples
            assertTrue("Invalid TTS sample rate: ${audio.sampleRate}", audio.sampleRate in 8_000..96_000)
            assertTrue("TTS returned less than half a second of audio", audio.samples.size >= audio.sampleRate / 2)
            assertTrue("TTS returned non-finite samples", audio.samples.all { it.isFinite() })
            assertTrue("TTS returned silence", audio.samples.any { abs(it) > 0.0001f })

            // The voice's rate is deliberately passed unchanged. sherpa performs resampling.
            val transcript = transcribe(directory, audio)
            Log.i(TAG, "sampleRate=${audio.sampleRate}; samples=${audio.samples.size}; transcript=$transcript")
            val normalized = transcript.lowercase(Locale.ROOT)
            for (word in listOf("quick", "brown", "fox")) {
                assertTrue("Expected '$word' in the Whisper result: $transcript", word in normalized)
            }
        } finally {
            samples?.fill(0f)
            directory.deleteRecursively()
        }
    }

    private fun synthesize(root: File): GeneratedAudio {
        val kitten = File(root, "kitten")
        val tts = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kitten = OfflineTtsKittenModelConfig(
                        model = File(kitten, "model.int8.onnx").absolutePath,
                        voices = File(kitten, "voices.bin").absolutePath,
                        tokens = File(kitten, "tokens.txt").absolutePath,
                        lexicon = File(kitten, "lexicon-en-us.txt").absolutePath,
                    ),
                    numThreads = 2,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        )
        return try {
            tts.generate("The quick brown fox jumps over the lazy dog.", sid = 0, speed = 1f)
        } finally {
            tts.release()
        }
    }

    private fun transcribe(root: File, audio: GeneratedAudio): String {
        val whisper = File(root, "whisper")
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(whisper, "tiny-encoder.int8.onnx").absolutePath,
                        decoder = File(whisper, "tiny-decoder.int8.onnx").absolutePath,
                        language = "en",
                        task = "transcribe",
                    ),
                    tokens = File(whisper, "tiny-tokens.txt").absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                ),
            ),
        )
        return try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(audio.samples, audio.sampleRate)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }

    private fun copyModelAssets(context: Context, root: File) {
        val files = mapOf(
            "whisper" to listOf("tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt"),
            "kitten" to listOf("model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-en-us.txt"),
        )
        for ((model, names) in files) {
            val destination = File(root, model).apply { check(mkdirs()) }
            for (name in names) {
                context.assets.open("models/$model/$name").use { input ->
                    File(destination, name).outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "NativeSpeechSmoke"
    }
}
