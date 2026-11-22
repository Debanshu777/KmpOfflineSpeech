package consumer

import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel
import io.github.debanshu.siren.speech.createJvmSpeechEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Distribution gate: public API, published JAR natives, and consumer-owned classpath models. */
class PublishedJvmNativeTest {
    @Test fun publishedJarCanRunBundledConsumerModels() {
        assumeTrue(System.getProperty("siren.native") == "true")
        assertNull(System.getProperty("sherpa_onnx.native.path"))
        fun bundled(path: String) = ModelFile.Bundled("models/$path")
        val engine = createJvmSpeechEngine(SherpaModels(
            recognition = WhisperModel(bundled("whisper/tiny-encoder.int8.onnx"),
                bundled("whisper/tiny-decoder.int8.onnx"), bundled("whisper/tiny-tokens.txt")),
            synthesis = KittenModel(bundled("kitten/model.int8.onnx"), bundled("kitten/voices.bin"),
                bundled("kitten/tokens.txt"), bundled("kitten/lexicon-en-us.txt")),
        ))
        runBlocking {
            var samples: FloatArray? = null
            try {
                val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
                samples = audio.samples
                assertTrue(audio.samples.all { it.isFinite() } && audio.samples.any { abs(it) > 0.0001f })
                val transcript = engine.transcribe(audio)
                val words = transcript.lowercase().split(Regex("[^a-z]+")).toSet()
                val matched = listOf("quick", "brown", "fox", "jumps", "lazy", "dog").count { it in words }
                println("PUBLISHED_JVM_NATIVE rate=${audio.sampleRate} samples=${audio.samples.size} matched=$matched/6 transcript=$transcript")
                assertTrue(matched >= 4, "Unrecognizable published runtime output: $transcript")
            } finally {
                try { engine.close() } finally { samples?.fill(0f) }
            }
        }
    }
}
