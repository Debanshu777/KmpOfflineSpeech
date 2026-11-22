package consumer

import android.content.Context
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine

fun androidEngine(context: Context, models: SherpaModels): SpeechEngine =
    createAndroidSpeechEngine(context, models)
