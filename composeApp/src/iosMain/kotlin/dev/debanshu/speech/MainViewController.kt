package dev.debanshu.speech

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    SpeechApp(remember { IosSpeechPlatform() })
}
