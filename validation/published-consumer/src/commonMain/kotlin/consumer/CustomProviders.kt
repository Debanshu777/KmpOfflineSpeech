package consumer

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.ModelSpeechEngine
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.SpeechRecognizer
import io.github.debanshu.siren.speech.SpeechSynthesizer

/** Third-party implementations compile against the published SPI, without sherpa imports. */
fun customEngine(): SpeechEngine = ModelSpeechEngine(
    recognizer = object : SpeechRecognizer {
        override val displayName = "Consumer recognizer"
        override suspend fun transcribe(audio: PcmAudio) = "${audio.samples.size}@${audio.sampleRate}"
    },
    synthesizer = object : SpeechSynthesizer {
        override val displayName = "Consumer synthesizer"
        override suspend fun synthesize(text: String) = PcmAudio(FloatArray(text.length) { 0.25f }, 22_050)
    },
)
