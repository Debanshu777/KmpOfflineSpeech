package consumer

import android.content.Context
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine
import io.github.debanshu.siren.speech.createSpeechEngine

/** Default integration needs only a Context and host-packaged standard model files. */
fun androidEngine(context: Context): SpeechEngine = createSpeechEngine(context)

fun configuredAndroidEngine(context: Context, models: SherpaModels): SpeechEngine =
    createSpeechEngine(context, models)

/** Keep the original platform factory in the artifact compatibility check. */
fun legacyAndroidEngine(context: Context, models: SherpaModels): SpeechEngine =
    createAndroidSpeechEngine(context, models)
