package io.github.debanshu.siren.speech

import io.github.debanshu.siren.audio.PcmAudio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpeechEngineExtensionsTest {
    @Test fun scopedUseReturnsTheResultAndClosesTheEngine() = runTest {
        var closes = 0
        val engine = ModelSpeechEngine(synthesizer = object : SpeechSynthesizer {
            override suspend fun synthesize(text: String): PcmAudio = PcmAudio(floatArrayOf(.1f), 24_000)
            override suspend fun close() { closes++ }
        })
        val result = engine.use { it.synthesize("Hello") }
        assertEquals(24_000, result.sampleRate)
        assertEquals(1, closes)
        assertFailsWith<IllegalStateException> { engine.synthesize("Closed") }
    }

    @Test fun cancellationStillCompletesSuspendingCleanup() = runTest {
        val entered = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val engine = object : SpeechEngine by ModelSpeechEngine() {
            override suspend fun close() {
                delay(1)
                closed.complete(Unit)
            }
        }
        val operation = launch {
            engine.use {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        operation.cancelAndJoin()
        assertTrue(closed.isCompleted)
    }

    @Test fun operationFailureIsPreservedWhenCleanupAlsoFails() = runTest {
        val original = IllegalArgumentException("operation")
        val cleanup = IllegalStateException("cleanup")
        val engine = object : SpeechEngine by ModelSpeechEngine() {
            override suspend fun close(): Unit = throw cleanup
        }
        val failure = assertFailsWith<IllegalArgumentException> { engine.use<Unit> { throw original } }
        assertSame(original, failure)
        assertEquals(listOf(cleanup), failure.suppressedExceptions)
        assertEquals("cleanup", assertFailsWith<IllegalStateException> { engine.use { "ok" } }.message)
    }

    @Test fun rawPcmOverloadPreservesTheActualRateAndArrayOwnership() = runTest {
        val samples = floatArrayOf(.2f, -.1f)
        val engine = ModelSpeechEngine(recognizer = object : SpeechRecognizer {
            override suspend fun transcribe(audio: PcmAudio): String {
                assertSame(samples, audio.samples)
                return "rate=${audio.sampleRate}"
            }
        })
        engine.use {
            assertEquals("rate=48000", it.transcribe(samples, 48_000))
            assertFailsWith<IllegalArgumentException> { it.transcribe(samples, 0) }
            assertFailsWith<IllegalArgumentException> { it.transcribe(floatArrayOf(), 48_000) }
        }
    }
}
