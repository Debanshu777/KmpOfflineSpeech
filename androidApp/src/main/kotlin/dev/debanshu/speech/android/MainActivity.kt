package dev.debanshu.speech.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.debanshu.speech.SpeechApp
import dev.debanshu.speech.createAndroidSpeechPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register the permission launcher before this Activity reaches STARTED.
        val speech = createAndroidSpeechPlatform(this)
        setContent { SpeechApp(speech) }
    }
}
