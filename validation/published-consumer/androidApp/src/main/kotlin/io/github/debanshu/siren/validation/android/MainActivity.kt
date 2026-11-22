package io.github.debanshu.siren.validation.android

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Published Siren SDK: ${SherpaModels().threads} default threads"
        })
    }

    // Compile the Android factory API; callers supply files before doing inference.
    fun configuredEngine(models: SherpaModels): SpeechEngine = createAndroidSpeechEngine(this, models)
}
