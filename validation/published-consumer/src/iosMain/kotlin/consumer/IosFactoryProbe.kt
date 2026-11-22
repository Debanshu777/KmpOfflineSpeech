package consumer

import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel
import io.github.debanshu.siren.speech.createIosSpeechEngine
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use

/** The host supplies the standard models folder in its app bundle. */
suspend fun synthesizeWithDefaultSdk(text: String): FloatArray =
    createSpeechEngine().use { it.synthesize(text).samples }

suspend fun transcribeWithDefaultSdk(samples: FloatArray, sampleRate: Int): String =
    createSpeechEngine().use { it.transcribe(samples, sampleRate) }

/** Dynamic framework exports keep real native inference paths reachable during linking. */
suspend fun synthesizeWithPublishedSdk(directory: String, text: String): FloatArray {
    val engine = createIosSpeechEngine(SherpaModels(synthesis = KittenModel(
        ModelFile.Local("$directory/voice.onnx"), ModelFile.Local("$directory/voices.bin"),
        ModelFile.Local("$directory/tokens.txt"), ModelFile.Local("$directory/lexicon.txt"),
    )))
    return engine.use { it.synthesize(text).samples }
}

suspend fun transcribeWithPublishedSdk(directory: String, samples: FloatArray, sampleRate: Int): String {
    val engine = createIosSpeechEngine(SherpaModels(recognition = WhisperModel(
        ModelFile.Local("$directory/encoder.onnx"), ModelFile.Local("$directory/decoder.onnx"),
        ModelFile.Local("$directory/tokens.txt"),
    )))
    return engine.use { it.transcribe(samples, sampleRate) }
}
