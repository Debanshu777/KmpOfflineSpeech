package consumer

import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel
import io.github.debanshu.siren.speech.createIosSpeechEngine

/** Dynamic framework exports keep real native inference paths reachable during linking. */
suspend fun synthesizeWithPublishedSdk(directory: String, text: String): FloatArray {
    val engine = createIosSpeechEngine(SherpaModels(synthesis = KittenModel(
        ModelFile.Local("$directory/voice.onnx"), ModelFile.Local("$directory/voices.bin"),
        ModelFile.Local("$directory/tokens.txt"), ModelFile.Local("$directory/lexicon.txt"),
    )))
    return try { engine.synthesize(text).samples } finally { engine.close() }
}

suspend fun transcribeWithPublishedSdk(directory: String, samples: FloatArray, sampleRate: Int): String {
    val engine = createIosSpeechEngine(SherpaModels(recognition = WhisperModel(
        ModelFile.Local("$directory/encoder.onnx"), ModelFile.Local("$directory/decoder.onnx"),
        ModelFile.Local("$directory/tokens.txt"),
    )))
    return try { engine.transcribe(PcmAudio(samples, sampleRate)) } finally { engine.close() }
}
