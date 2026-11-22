package io.github.debanshu.siren.sample

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.SpeechCapabilities

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SpeechControllerTest {
    @Test fun missingRecognitionDoesNotOpenMicrophoneAndStillAllowsSpeech() = runTest {
        val fake = FakePlatform().apply { capabilities = SpeechCapabilities(false, true) }
        val controller = SpeechController(fake, backgroundScope)
        controller.record()
        runCurrent()
        assertEquals(0, fake.starts)
        assertContains(controller.state.value.error.orEmpty(), "recognition model")
        controller.speak()
        runCurrent()
        assertEquals(1, fake.plays)
        assertEquals(Phase.Idle, controller.state.value.phase)
        controller.close()
        runCurrent()
    }

    @Test fun missingSynthesisDoesNotStartInference() = runTest {
        val fake = FakePlatform().apply { capabilities = SpeechCapabilities(true, false) }
        val controller = SpeechController(fake, backgroundScope)
        controller.speak()
        runCurrent()
        assertEquals(0, fake.plays)
        assertFalse(fake.inferenceActive)
        assertContains(controller.state.value.error.orEmpty(), "synthesis model")
        controller.close()
        runCurrent()
    }

    @Test fun deniedPermissionDoesNotOpenMicrophone() = runTest {
        val fake = FakePlatform().apply { permission = false }
        val controller = SpeechController(fake, backgroundScope)
        controller.record()
        runCurrent()
        assertEquals(0, fake.starts)
        assertEquals(Phase.Idle, controller.state.value.phase)
        assertContains(controller.state.value.error.orEmpty(), "permission")
        controller.close()
        runCurrent()
    }

    @Test fun recordingAutoStopsAndTranscribesAtTwentySeconds() = runTest {
        val fake = FakePlatform()
        val controller = SpeechController(fake, backgroundScope)
        controller.record()
        runCurrent()
        assertEquals(Phase.Recording, controller.state.value.phase)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals("recognized words", controller.state.value.text)
        assertEquals(1, fake.recordingStops)
        assertEquals(Phase.Idle, controller.state.value.phase)
        controller.close()
        runCurrent()
    }

    @Test fun cancellationWaitsForNativeOwnershipBeforeTeardown() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fake = FakePlatform().apply { synthesisGate = gate }
        val controller = SpeechController(fake, backgroundScope)
        controller.speak()
        runCurrent()
        assertTrue(fake.inferenceActive)
        controller.stop()
        runCurrent()
        assertFalse(fake.stoppedDuringInference)
        assertEquals(Phase.Stopping, controller.state.value.phase)
        gate.complete(Unit)
        runCurrent()
        assertFalse(fake.stoppedDuringInference)
        assertEquals(0, fake.plays)
        assertEquals(Phase.Idle, controller.state.value.phase)
        controller.close()
        runCurrent()
    }

    private class FakePlatform : SpeechPlatform {
        override var capabilities = SpeechCapabilities(true, true)
        var permission = true
        var starts = 0
        var recordingStops = 0
        var plays = 0
        var inferenceActive = false
        var stoppedDuringInference = false
        var synthesisGate: CompletableDeferred<Unit>? = null
        override suspend fun requestMicrophonePermission() = permission
        override suspend fun startRecording() { starts++ }
        override suspend fun stopRecording(): PcmAudio {
            recordingStops++
            return PcmAudio(FloatArray(16_000), 16_000)
        }
        override suspend fun transcribe(audio: PcmAudio) = "recognized words"
        override suspend fun synthesize(text: String): PcmAudio {
            inferenceActive = true
            try { withContext(NonCancellable) { synthesisGate?.await() } }
            finally { inferenceActive = false }
            coroutineContext.ensureActive()
            return PcmAudio(FloatArray(100), 24_000)
        }
        override suspend fun play(audio: PcmAudio) { plays++ }
        override suspend fun stop() { stoppedDuringInference = stoppedDuringInference || inferenceActive }
        override suspend fun close() { stop() }
    }
}
