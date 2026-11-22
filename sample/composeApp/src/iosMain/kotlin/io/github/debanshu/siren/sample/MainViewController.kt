package io.github.debanshu.siren.sample

import io.github.debanshu.siren.sample.platform.IosSpeechPlatform

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    SirenApp(remember { IosSpeechPlatform() })
}
