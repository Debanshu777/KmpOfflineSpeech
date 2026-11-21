package dev.debanshu.speech

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
fun SpeechApp(platform: SpeechPlatform) {
    val controller = remember(platform) {
        SpeechController(platform, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop() }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Offline Speech", style = MaterialTheme.typography.headlineLarge)
                Text("Whisper listens. Kitten speaks.\nPowered by Kotlin Multiplatform.", style = MaterialTheme.typography.bodyLarge)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("1  Say something", style = MaterialTheme.typography.titleMedium)
                        Text("Record a short phrase, up to 20 seconds. Transcription starts when you stop.")
                        Button(
                            onClick = { if (state.phase == Phase.Recording) controller.transcribe() else controller.record() },
                            enabled = state.phase == Phase.Idle || state.phase == Phase.Recording,
                        ) { Text(if (state.phase == Phase.Recording) "Stop & transcribe" else "Record") }
                    }
                }
                Text("2  Make it speak", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.text, onValueChange = controller::edit,
                    label = { Text("Text to read aloud") }, minLines = 4,
                    enabled = state.phase == Phase.Idle, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("English voice · up to 500 characters") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = controller::speak, enabled = state.phase == Phase.Idle && state.text.isNotBlank()) { Text("Speak") }
                    OutlinedButton(onClick = controller::stop, enabled = state.phase != Phase.Idle && state.phase != Phase.Stopping) { Text("Cancel / stop") }
                }
                Text(when (state.phase) {
                    Phase.Idle -> "Ready · inference stays on this device"
                    Phase.Starting -> "Opening microphone…"
                    Phase.Recording -> "Listening… (20 seconds maximum)"
                    Phase.Transcribing -> "Transcribing…"
                    Phase.Synthesizing -> "Generating speech…"
                    Phase.Playing -> "Speaking…"
                    Phase.Stopping -> "Stopping…"
                }, style = MaterialTheme.typography.labelLarge)
                if (state.phase in listOf(Phase.Starting, Phase.Transcribing, Phase.Synthesizing, Phase.Stopping)) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
