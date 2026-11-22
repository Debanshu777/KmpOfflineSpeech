package consumer

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.ModelSpeechEngine
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechRecognizer
import io.github.debanshu.siren.speech.WhisperModel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublishedApiTest {
    @Test fun customProvidersWorkThroughPublishedContracts() = runTest {
        val engine = customEngine()
        assertEquals("Consumer recognizer", engine.capabilities.recognitionLabel)
        assertEquals("Consumer synthesizer", engine.capabilities.synthesisLabel)
        val audio = engine.synthesize("external consumer")
        assertEquals(22_050, audio.sampleRate)
        assertEquals("17@22050", engine.transcribe(audio))
        engine.close()
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
}
