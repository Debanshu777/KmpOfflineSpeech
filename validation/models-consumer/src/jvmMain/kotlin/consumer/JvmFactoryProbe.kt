package consumer

import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createJvmSpeechEngine
import io.github.debanshu.siren.speech.createSpeechEngine

fun jvmEngine(): SpeechEngine = createSpeechEngine()

/** The original explicit-model factory remains source compatible. */
fun legacyJvmEngine(models: SherpaModels): SpeechEngine = createJvmSpeechEngine(models)
