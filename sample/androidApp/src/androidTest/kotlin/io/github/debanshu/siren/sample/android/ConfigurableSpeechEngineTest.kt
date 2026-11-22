package io.github.debanshu.siren.sample.android

import io.github.debanshu.siren.sample.SampleModels

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.createAndroidSpeechEngine
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Caller-selected files exercise the public engine without an Activity or microphone. */
@RunWith(AndroidJUnit4::class)
class ConfigurableSpeechEngineTest {
    @Test(timeout = 120_000)
    fun suppliedLocalFilesAndModelOptionsCanRunWithoutTheSamplePlatform() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "siren-local-models-${System.nanoTime()}")
        check(directory.mkdirs())
        val preset = SampleModels.bundledDemo()
        val whisper = checkNotNull(preset.recognition)
        val kitten = checkNotNull(preset.synthesis)
        fun local(source: ModelFile, name: String): ModelFile.Local {
            check(source is ModelFile.Bundled)
            val target = File(directory, name)
            context.assets.open(source.path).use { input -> target.outputStream().use { input.copyTo(it) } }
            return ModelFile.Local(target.absolutePath)
        }
        var generatedSamples: FloatArray? = null
        try {
            // These deliberately differ from the demo filenames and voice settings.
            val selected = SherpaModels(
                recognition = whisper.copy(
                    encoder = local(whisper.encoder, "selected-encoder.onnx"),
                    decoder = local(whisper.decoder, "selected-decoder.onnx"),
                    tokens = local(whisper.tokens, "selected-recognition-tokens.txt"),
                    language = "en",
                    task = "transcribe",
                    displayName = "Selected Whisper",
                ),
                synthesis = kitten.copy(
                    model = local(kitten.model, "selected-voice.onnx"),
                    voices = local(kitten.voices, "selected-speakers.bin"),
                    tokens = local(kitten.tokens, "selected-synthesis-tokens.txt"),
                    lexicon = local(kitten.lexicon, "selected-lexicon.txt"),
                    speakerId = 1,
                    speed = 1.1f,
                    displayName = "Selected Kitten",
                ),
                threads = 1,
            )
            val engine = createAndroidSpeechEngine(context, selected)
            runBlocking {
                try {
                    assertTrue(engine.capabilities.recognition)
                    assertTrue(engine.capabilities.synthesis)
                    assertEquals("Selected Whisper", engine.capabilities.recognitionLabel)
                    assertEquals("Selected Kitten", engine.capabilities.synthesisLabel)
                    val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
                    generatedSamples = audio.samples
                    assertTrue(audio.sampleRate in 8_000..96_000)
                    assertTrue(audio.samples.isNotEmpty())
                    val transcript = engine.transcribe(audio)
                    val words = transcript.lowercase(Locale.ROOT).split(Regex("[^a-z]+")).toSet()
                    val recognized = listOf("quick", "brown", "fox", "jumps", "lazy", "dog").count { it in words }
                    assertTrue("Expected recognizable speech from the selected models: $transcript", recognized >= 4)
                } finally {
                    engine.close()
                }
            }
        } finally {
            generatedSamples?.fill(0f)
            directory.deleteRecursively()
        }
    }

}
