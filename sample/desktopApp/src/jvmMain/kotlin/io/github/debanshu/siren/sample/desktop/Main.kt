package io.github.debanshu.siren.sample.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.debanshu.siren.sample.SirenApp
import io.github.debanshu.siren.sample.platform.JvmSpeechPlatform
import kotlinx.coroutines.launch

fun main() = application {
    val platform = remember { JvmSpeechPlatform() }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = {
            if (!closing) {
                closing = true
                scope.launch {
                    try { platform.close() } finally { exitApplication() }
                }
            }
        },
        title = "Siren",
        state = rememberWindowState(width = 460.dp, height = 820.dp),
    ) {
        SirenApp(platform)
    }
}
