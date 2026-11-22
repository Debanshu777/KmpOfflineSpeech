package io.github.debanshu.siren.validation.android

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine
import io.github.debanshu.siren.speech.createSpeechEngine

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Published Siren SDK: ${SherpaModels().threads} default threads"
        })
    }

    // Compile the minimal API. The host must package the standard models before inference.
    fun defaultEngine(): SpeechEngine = createSpeechEngine(this)

    // The original factory and explicit model descriptors remain supported.
    fun configuredEngine(models: SherpaModels): SpeechEngine = createAndroidSpeechEngine(this, models)
}
