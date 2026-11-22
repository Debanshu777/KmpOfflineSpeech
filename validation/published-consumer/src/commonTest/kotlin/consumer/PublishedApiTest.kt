package consumer

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.ModelSpeechEngine
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechRecognizer
import io.github.debanshu.siren.speech.WhisperModel
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublishedApiTest {
    @Test fun customProvidersWorkThroughPublishedContracts() = runTest {
        val engine = customEngine()
        val audio = engine.use {
            assertEquals("Consumer recognizer", it.capabilities.recognitionLabel)
            assertEquals("Consumer synthesizer", it.capabilities.synthesisLabel)
            val audio = it.synthesize("external consumer")
            assertEquals(22_050, audio.sampleRate)
            assertEquals("17@22050", it.transcribe(audio.samples, audio.sampleRate))
            audio
        }
        assertFailsWith<IllegalStateException> { engine.transcribe(audio) }
    }

    @Test fun recognitionOnlyProviderHasNoImplicitVoice() = runTest {
        val engine = ModelSpeechEngine(recognizer = object : SpeechRecognizer {
            override suspend fun transcribe(audio: PcmAudio) = "configured independently"
        })
        assertTrue(engine.capabilities.recognition)
        assertFalse(engine.capabilities.synthesis)
        assertFailsWith<UnsupportedOperationException> { engine.synthesize("no voice") }
        engine.close()
    }

    @Test fun modelDescriptorsAcceptConsumerSelectedFilesAndParameters() {
        val models = SherpaModels(
            recognition = WhisperModel(ModelFile.Local("/models/encoder-v2.onnx"),
                ModelFile.Local("/models/decoder-v2.onnx"), ModelFile.Local("/models/vocabulary.txt"),
                language = "hi", task = "translate", displayName = "Selected recognizer"),
            synthesis = KittenModel(ModelFile.Bundled("custom/voice.onnx"),
                ModelFile.Bundled("custom/speakers.bin"), ModelFile.Bundled("custom/tokens.txt"),
                ModelFile.Bundled("custom/lexicon.txt"), speakerId = 2, speed = 1.2f,
                displayName = "Selected voice"),
            threads = 3,
        )
        assertEquals("hi", models.recognition?.language)
        assertEquals("translate", models.recognition?.task)
        assertEquals(2, models.synthesis?.speakerId)
        assertEquals(1.2f, models.synthesis?.speed)
        assertEquals(3, models.threads)
    }

    @Test fun standardLayoutsNeedNoIndividualModelMapping() {
        val bundled = SherpaModels.bundled(synthesis = false)
        assertEquals(ModelFile.Bundled("models/whisper/tiny-encoder.int8.onnx"), bundled.recognition?.encoder)
        assertNull(bundled.synthesis)
        val local = SherpaModels.local("/consumer/models", recognition = false, threads = 3)
        assertNull(local.recognition)
        assertEquals(ModelFile.Local("/consumer/models/kitten/model.int8.onnx"), local.synthesis?.model)
        assertEquals(3, local.threads)
    }
}
