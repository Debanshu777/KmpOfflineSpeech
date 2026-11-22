package consumer

import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use
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
        runBlocking {
            var samples: FloatArray? = null
            try {
                createSpeechEngine().use { engine ->
                    val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
                    samples = audio.samples
                    assertTrue(audio.samples.all { it.isFinite() } && audio.samples.any { abs(it) > 0.0001f })
                    val transcript = engine.transcribe(audio.samples, audio.sampleRate)
                    val words = transcript.lowercase().split(Regex("[^a-z]+")).toSet()
                    val matched = listOf("quick", "brown", "fox", "jumps", "lazy", "dog").count { it in words }
                    println("PUBLISHED_JVM_NATIVE api=createSpeechEngine/use/transcribe(samples,rate) rate=${audio.sampleRate} samples=${audio.samples.size} matched=$matched/6 transcript=$transcript")
                    assertTrue(matched >= 4, "Unrecognizable published runtime output: $transcript")
                }
            } finally {
                samples?.fill(0f)
            }
        }
    }
}
