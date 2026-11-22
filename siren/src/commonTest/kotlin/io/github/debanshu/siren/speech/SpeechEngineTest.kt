package io.github.debanshu.siren.speech

import io.github.debanshu.siren.audio.PcmAudio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SpeechEngineTest {
    @Test fun recognitionOnlyAcceptsAnotherProviderWithoutSynthesis() = runTest {
        var calls = 0
        val engine = ModelSpeechEngine(recognizer = object : SpeechRecognizer {
            override val displayName = "Alternative recognizer"
            override suspend fun transcribe(audio: PcmAudio): String {
                calls++
                return "custom ${audio.sampleRate}"
            }
        })
        assertEquals(SpeechCapabilities(true, false, "Alternative recognizer", null), engine.capabilities)
        assertEquals("custom 48000", engine.transcribe(PcmAudio(FloatArray(100), 48_000)))
        assertFailsWith<UnsupportedOperationException> { engine.synthesize("Hello") }
        assertEquals(1, calls)
        engine.close()
        assertFailsWith<IllegalStateException> { engine.transcribe(PcmAudio(floatArrayOf(0f), 16_000)) }
    }

    @Test fun synthesisOnlyReturnsProviderRateAndClosesOnce() = runTest {
        var closes = 0
        val engine = ModelSpeechEngine(synthesizer = object : SpeechSynthesizer {
            override suspend fun synthesize(text: String) = PcmAudio(floatArrayOf(.1f), 22_050)
            override suspend fun close() { closes++ }
        })
        assertFalse(engine.capabilities.recognition)
        assertTrue(engine.capabilities.synthesis)
        assertEquals(22_050, engine.synthesize("Hello").sampleRate)
        assertFailsWith<UnsupportedOperationException> { engine.transcribe(PcmAudio(floatArrayOf(0f), 16_000)) }
        engine.close()
        engine.close()
        assertEquals(1, closes)
    }

    @Test fun noProvidersDisablesBothCapabilities() = runTest {
        val engine = ModelSpeechEngine()
        assertEquals(SpeechCapabilities(false, false), engine.capabilities)
        assertFailsWith<UnsupportedOperationException> { engine.synthesize("Hello") }
        engine.close()
    }

    @Test fun closeWaitsForCancelledNativeWorkAndDiscardsItsAudio() = runTest {
        val gate = CompletableDeferred<Unit>()
        val samples = floatArrayOf(.5f, -.5f)
        var closed = false
        val engine = ModelSpeechEngine(synthesizer = object : SpeechSynthesizer {
            override suspend fun synthesize(text: String): PcmAudio = withContext(NonCancellable) {
                gate.await()
                PcmAudio(samples, 24_000)
            }
            override suspend fun close() { closed = true }
        })
        val inference = backgroundScope.launch { engine.synthesize("Hello") }
        runCurrent()
        inference.cancel()
        val teardown = backgroundScope.launch { engine.close() }
        runCurrent()
        assertFalse(closed)
        assertFalse(teardown.isCompleted)
        gate.complete(Unit)
        runCurrent()
        assertTrue(closed)
        assertContentEquals(floatArrayOf(0f, 0f), samples)
    }

    @Test fun bothProvidersAreReleasedIfOneCloseFails() = runTest {
        var synthesisClosed = false
        val engine = ModelSpeechEngine(
            recognizer = object : SpeechRecognizer {
                override suspend fun transcribe(audio: PcmAudio) = "text"
                override suspend fun close(): Unit = error("release failed")
            },
            synthesizer = object : SpeechSynthesizer {
                override suspend fun synthesize(text: String) = PcmAudio(floatArrayOf(0f), 24_000)
                override suspend fun close() { synthesisClosed = true }
            },
        )
        assertFailsWith<IllegalStateException> { engine.close() }
        assertTrue(synthesisClosed)
        engine.close()
    }

    @Test fun presetCanRemoveEitherModelAndRejectUnsafeBundlePaths() {
        val file = ModelFile.Local("/models/example")
        val preset = SherpaModels(
            recognition = WhisperModel(file, file, file),
            synthesis = KittenModel(file, file, file, file),
        )
        assertNull(preset.copy(recognition = null).recognition)
        assertEquals(preset.synthesis, preset.copy(recognition = null).synthesis)
        assertFailsWith<IllegalArgumentException> { ModelFile.Bundled("../outside.onnx") }
        assertFailsWith<IllegalArgumentException> { ModelFile.Bundled("/absolute.onnx") }
        assertFailsWith<IllegalArgumentException> { ModelFile.Bundled("models/./model.onnx") }
        assertFailsWith<IllegalArgumentException> { SherpaModels(threads = 0) }
        assertFailsWith<IllegalArgumentException> { preset.synthesis!!.copy(speed = Float.NaN) }
    }
}
