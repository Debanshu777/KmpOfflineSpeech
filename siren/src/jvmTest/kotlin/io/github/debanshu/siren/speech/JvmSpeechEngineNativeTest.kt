package io.github.debanshu.siren.speech

import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt in with -Pnative=true -PmodelsDir=/absolute/path; ordinary JVM tests load no models. */
class JvmSpeechEngineNativeTest {
    @Test
    fun packagedRuntimeCanSynthesizeAndRecognizeLocalModels() {
        assumeTrue("Enable the native smoke test explicitly.", System.getProperty("siren.native") == "true")
        val root = File(checkNotNull(System.getProperty("siren.modelsDir")) { "Set -PmodelsDir=/absolute/path" })
        require(root.isAbsolute && root.isDirectory) { "modelsDir must be an existing absolute directory." }
        assertEquals(null, System.getProperty("sherpa_onnx.native.path"), "The smoke test must use packaged natives.")
        fun local(path: String): ModelFile = ModelFile.Local(root.resolve(path).absolutePath)
        val engine = createJvmSpeechEngine(
            SherpaModels(
                recognition = WhisperModel(
                    encoder = local("whisper/tiny-encoder.int8.onnx"),
                    decoder = local("whisper/tiny-decoder.int8.onnx"),
                    tokens = local("whisper/tiny-tokens.txt"),
                    displayName = "Whisper native fixture",
                ),
                synthesis = KittenModel(
                    model = local("kitten/model.int8.onnx"), voices = local("kitten/voices.bin"),
                    tokens = local("kitten/tokens.txt"), lexicon = local("kitten/lexicon-en-us.txt"),
                    displayName = "Kitten native fixture",
                ),
            ),
        )
        runBlocking {
            var samples: FloatArray? = null
            try {
                val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
                samples = audio.samples
                assertTrue(audio.sampleRate in 8_000..96_000)
                assertTrue(audio.samples.all { it.isFinite() })
                assertTrue(audio.samples.any { abs(it) > 0.0001f })
                val transcript = engine.transcribe(audio)
                val words = transcript.lowercase(Locale.ROOT).split(Regex("[^a-z]+")).toSet()
                val matched = listOf("quick", "brown", "fox", "jumps", "lazy", "dog").count { it in words }
                println("SIREN_JVM_NATIVE os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")} rate=${audio.sampleRate} samples=${audio.samples.size} matched=$matched/6 transcript=$transcript")
                assertTrue(matched >= 4, "Expected recognizable speech from packaged JVM natives: $transcript")
            } finally {
                try { engine.close() } finally { samples?.fill(0f) }
            }
        }
    }
}
