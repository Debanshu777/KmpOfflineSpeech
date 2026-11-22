package io.github.debanshu.siren.sample.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.debanshu.siren.sample.SampleModels
import io.github.debanshu.siren.speech.createAndroidSpeechEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

/** Exercises the SDK public API and packaged native runtime without microphone or playback. */
@RunWith(AndroidJUnit4::class)
class NativeSpeechSmokeTest {
    @Test(timeout = 120_000)
    fun kittenAudioCanBeTranscribedByWhisper() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = createAndroidSpeechEngine(context, SampleModels.bundledDemo())
        var samples: FloatArray? = null
        try {
            val audio = engine.synthesize("The quick brown fox jumps over the lazy dog.")
            samples = audio.samples
            assertTrue(audio.sampleRate in 8_000..96_000)
            assertTrue(audio.samples.size >= audio.sampleRate / 2)
            assertTrue(audio.samples.all { it.isFinite() })
            assertTrue(audio.samples.any { abs(it) > .0001f })
            val transcript = engine.transcribe(audio)
            Log.i("NativeSpeechSmoke", "sampleRate=" + audio.sampleRate + "; samples=" + audio.samples.size + "; transcript=" + transcript)
            val normalized = transcript.lowercase(Locale.ROOT)
            for (word in listOf("quick", "brown", "fox")) {
                assertTrue("Expected recognizable speech: " + transcript, word in normalized)
            }
        } finally {
            samples?.fill(0f)
            engine.close()
        }
    }
}
