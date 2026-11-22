package io.github.debanshu.siren.sample

import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel

object SampleModels {
    /** Explicit sample preset; callers can select different files or their own provider. */
    fun bundledDemo(): SherpaModels = SherpaModels(
        recognition = WhisperModel(
            ModelFile.Bundled("models/whisper/tiny-encoder.int8.onnx"),
            ModelFile.Bundled("models/whisper/tiny-decoder.int8.onnx"),
            ModelFile.Bundled("models/whisper/tiny-tokens.txt"),
            displayName = "Whisper tiny",
        ),
        synthesis = KittenModel(
            ModelFile.Bundled("models/kitten/model.int8.onnx"),
            ModelFile.Bundled("models/kitten/voices.bin"),
            ModelFile.Bundled("models/kitten/tokens.txt"),
            ModelFile.Bundled("models/kitten/lexicon-en-us.txt"),
            displayName = "Kitten nano · English",
        ),
    )
}
