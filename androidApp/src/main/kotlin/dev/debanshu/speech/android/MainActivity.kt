package dev.debanshu.speech.android

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.debanshu.speech.SpeechApp
import dev.debanshu.speech.SpeechPlatform
import dev.debanshu.speech.createAndroidSpeechPlatform

class MainActivity : ComponentActivity() {
    internal lateinit var speech: SpeechPlatform
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Register the permission launcher before this Activity reaches STARTED.
        speech = createAndroidSpeechPlatform(this)
        setContent { SpeechApp(speech) }
    }
}
