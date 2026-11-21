@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.debanshu777.speech

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

private val Background = Color(0xFFF5F6F8)
private val Ink = Color(0xFF20252B)
private val Secondary = Color(0xFF69717B)
private val Hairline = Color(0xFFE0E4E8)
private val RecordRed = Color(0xFFC63C32)

/** Focused-recorder direction; Unslop by Damir Sokolovsky, CC BY 4.0. See docs/demo.md. */
@Composable
fun SpeechApp(platform: SpeechPlatform, modifier: Modifier = Modifier) {
    val controller = remember(platform) {
        SpeechController(platform, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.close() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop() }
    var recordedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.phase) {
        if (state.phase == Phase.Starting) recordedMillis = 0L
        if (state.phase == Phase.Recording) {
            val start = TimeSource.Monotonic.markNow()
            while (true) {
                recordedMillis = start.elapsedNow().inWholeMilliseconds.coerceAtMost(20_000L)
                delay(100)
            }
        }
    }
    val recording = state.phase == Phase.Recording
    val idle = state.phase == Phase.Idle
    val seconds = (recordedMillis / 1_000).toInt()
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Color.White, background = Background,
        surface = Color.White, onSurface = Ink, onBackground = Ink, error = RecordRed,
    )) {
        Surface(modifier.fillMaxSize(), color = Background) {
            BoxWithConstraints(Modifier.safeDrawingPadding().imePadding()) {
                val scrolling = maxHeight < 660.dp || LocalDensity.current.fontScale > 1.2f
                Column(
                    Modifier.fillMaxSize()
                        .then(if (scrolling) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Offline Speech", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Text("On device", color = Secondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice to text", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Whisper tiny", color = Secondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        RecordControl(recording, idle || recording, recordedMillis / 20_000f) {
                            if (recording) controller.transcribe() else controller.record()
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("00:${seconds.toString().padStart(2, '0')}", color = Ink,
                                fontSize = 40.sp, lineHeight = 44.sp,
                                style = TextStyle(fontFeatureSettings = "tnum"))
                            Text(if (recording) "Stop & transcribe" else "Record", color = Ink,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Up to 20 seconds", color = Secondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Transcript", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text("${state.text.length} / 500", color = Secondary, fontSize = 12.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                    Spacer(Modifier.height(12.dp))
                    TranscriptPanel(state, controller,
                        if (scrolling) Modifier.fillMaxWidth().heightIn(min = 280.dp)
                        else Modifier.fillMaxWidth().weight(1f), flexible = !scrolling)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(status(state.phase), color = if (recording) RecordRed else Secondary,
                                fontSize = 13.sp, modifier = Modifier.weight(1f).testTag("speech_status"))
                            if (state.phase in listOf(Phase.Starting, Phase.Recording, Phase.Transcribing)) {
                                TextButton(onClick = controller::stop) { Text("Cancel", fontSize = 13.sp) }
                            }
                        }
                        if (state.phase in listOf(Phase.Starting, Phase.Transcribing, Phase.Synthesizing, Phase.Stopping)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ink, trackColor = Hairline)
                        }
                        state.error?.let { Text(it, color = RecordRed, fontSize = 14.sp, lineHeight = 20.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordControl(recording: Boolean, enabled: Boolean, progress: Float, onClick: () -> Unit) {
    Box(Modifier.size(96.dp).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag("record_control")
        .semantics { contentDescription = if (recording) "Stop & transcribe" else "Record" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2
            drawCircle(Hairline, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
            if (recording) drawArc(RecordRed, -90f, 360f * progress.coerceIn(0f, 1f), false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(Modifier.size(if (recording) 32.dp else 64.dp)
            .background(RecordRed.copy(alpha = if (enabled) 1f else .3f),
                if (recording) RoundedCornerShape(4.dp) else CircleShape))
    }
}

@Composable
private fun TranscriptPanel(state: SpeechUiState, controller: SpeechController, modifier: Modifier, flexible: Boolean) {
    val active = state.phase == Phase.Synthesizing || state.phase == Phase.Playing
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
        .border(1.dp, Hairline, RoundedCornerShape(12.dp))) {
        BasicTextField(
            value = state.text, onValueChange = controller::edit,
            enabled = state.phase == Phase.Idle, minLines = if (flexible) 1 else 5,
            textStyle = TextStyle(color = Ink, fontSize = 22.sp, lineHeight = 32.sp),
            cursorBrush = SolidColor(RecordRed),
            modifier = Modifier.fillMaxWidth()
                .then(if (flexible) Modifier.weight(1f) else Modifier.heightIn(min = 160.dp))
                .padding(20.dp).testTag("transcript_text")
                .semantics { contentDescription = "Text to read aloud" },
        )
        HorizontalDivider(color = Hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Read aloud", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Kitten nano · English", color = Secondary, fontSize = 12.sp)
            }
            Button(onClick = { if (active) controller.stop() else controller.speak() },
                enabled = active || (state.phase == Phase.Idle && state.text.isNotBlank()),
                modifier = Modifier.heightIn(min = 48.dp).testTag("play_control"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Canvas(Modifier.size(16.dp)) {
                    if (active) drawRect(Color.White, topLeft = Offset(size.width * .15f, size.height * .15f),
                        size = Size(size.width * .7f, size.height * .7f))
                    else drawPath(Path().apply {
                        moveTo(size.width * .2f, size.height * .1f)
                        lineTo(size.width * .9f, size.height * .5f)
                        lineTo(size.width * .2f, size.height * .9f)
                        close()
                    }, Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (active) "Stop" else "Speak", fontSize = 14.sp)
            }
        }
    }
}

private fun status(phase: Phase) = when (phase) {
    Phase.Idle -> "Ready"
    Phase.Starting -> "Opening microphone"
    Phase.Recording -> "Listening"
    Phase.Transcribing -> "Transcribing"
    Phase.Synthesizing -> "Generating speech"
    Phase.Playing -> "Speaking"
    Phase.Stopping -> "Stopping"
}
