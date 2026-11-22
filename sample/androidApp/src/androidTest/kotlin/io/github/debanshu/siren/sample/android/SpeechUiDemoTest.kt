package io.github.debanshu.siren.sample.android

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.debanshu.siren.audio.PcmAudio
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real callbacks and native audio through public Compose test roots, without Espresso or accessibility. */
@RunWith(AndroidJUnit4::class)
class SpeechUiDemoTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val observedStatuses = linkedSetOf<String>()

    @Test(timeout = 120_000)
    fun recordTranscribeAndSpeakThroughTheRealScreen() {
        // These checks run before ActivityScenario launches the application.
        assumeTrue(
            "Opt in with -Pandroid.testInstrumentationRunnerArguments.demo=true on an unlocked device.",
            InstrumentationRegistry.getArguments().getString("demo") == "true",
        )
        assumeTrue("This fixture is verified on Android 10 or newer.", Build.VERSION.SDK_INT >= 29)
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assertTrue("Unlock the phone before the demo. The test never bypasses the lock screen.", !keyguard.isDeviceLocked)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertTrue(
            "Disconnect external audio devices before the speaker-to-microphone demo.",
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in EXTERNAL_OUTPUT_TYPES },
        )
        assertTrue("Grant microphone access before the demo (see docs/demo.md).",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        assertTrue("Set an audible media volume before the demo.",
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0)
        ActivityScenario.launch(MainActivity::class.java).use { active ->
            scenario = active
            val platform = onActivity { it.speech }
            var stimulus: PcmAudio? = null
            try {
                val preparedStimulus = runBlocking { platform.synthesize(FIXED_SENTENCE) }
                stimulus = preparedStimulus
                awaitStatus("Ready", 20_000)
                Log.i(TAG, "DEMO_VISIBLE")
                Thread.sleep(1_000)
                clickControl("record_control")
                awaitStatus("Listening", 10_000)
                Thread.sleep(600)
                // The real controller records its microphone. This is only an acoustic stimulus.
                runBlocking { platform.play(preparedStimulus) }
                Thread.sleep(700)
                clickControl("record_control")
                val transcript = awaitGenuineTranscript(45_000)
                Log.i(TAG, "RECOGNIZED_TRANSCRIPT=$transcript; contentWords=${matchedContentWords(transcript)}/6; characters=${transcript.length}")

                clickControl("play_control")
                awaitPlaybackAndReturnToReady(45_000)
                Log.i(TAG, "transcript=$transcript; contentWords=${matchedContentWords(transcript)}/6; observedStatuses=$observedStatuses")
                Thread.sleep(2_000)
            } finally {
                runBlocking { platform.stop() }
                stimulus?.samples?.fill(0f)
            }
        }
    }

    private fun awaitStatus(expected: String, timeoutMillis: Long) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            if (lastStatus == expected) return
            Thread.sleep(100)
        }
        error("Expected '$expected'; last status='$lastStatus'; observed=$observedStatuses")
    }

    private fun awaitGenuineTranscript(timeoutMillis: Long): String {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var lastTranscript = ""
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            lastTranscript = readText("transcript_text")
            if (lastStatus == "Ready" && matchedContentWords(lastTranscript) >= 4) return lastTranscript
            Thread.sleep(100)
        }
        error("No recognized UI transcript: status='$lastStatus'; transcript='$lastTranscript'; observed=$observedStatuses")
    }

    private fun awaitPlaybackAndReturnToReady(timeoutMillis: Long) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var sawPlaybackWork = false
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            if (lastStatus == "Generating speech" || lastStatus == "Speaking") sawPlaybackWork = true
            if (sawPlaybackWork && lastStatus == "Ready") return
            Thread.sleep(100)
        }
        error("UI playback did not complete: status='$lastStatus'; saw work=$sawPlaybackWork; observed=$observedStatuses")
    }

    private fun readStatus(): String = readText("speech_status").also {
        if (it.isNotBlank() && observedStatuses.add(it)) Log.i(TAG, "UI_STATUS=$it")
    }

    private fun readText(tag: String): String = withSemantics { nodes ->
        nodes.firstOrNull { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag] == tag }
            ?.let(::nodeText).orEmpty()
    }

    private fun nodeText(node: SemanticsNode): String {
        val config = node.config
        return when {
            config.contains(SemanticsProperties.EditableText) -> config[SemanticsProperties.EditableText].text
            config.contains(SemanticsProperties.Text) -> config[SemanticsProperties.Text].joinToString(" ") { it.text }
            else -> node.children.joinToString(" ") { nodeText(it) }
        }.trim()
    }

    private fun clickControl(tag: String) = withSemantics { nodes ->
        val node = nodes.firstOrNull { it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag] == tag }
            ?: error("No real Compose control with tag '$tag'")
        check(!node.config.contains(SemanticsProperties.Disabled)) { "Control '$tag' is disabled" }
        check(node.config.contains(SemanticsActions.OnClick)) { "Control '$tag' has no click callback" }
        val action = checkNotNull(node.config[SemanticsActions.OnClick].action)
        check(action()) { "Control '$tag' rejected its click" }
    }

    private fun <T> withSemantics(block: (List<SemanticsNode>) -> T): T = onActivity { activity ->
        val roots = mutableListOf<ViewRootForTest>()
        fun visit(view: View) {
            if (view is ViewRootForTest) roots += view
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(activity.window.decorView)
        roots.forEach { it.measureAndLayoutForTest() }
        block(roots.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false) })
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        var result: Result<T>? = null
        scenario.onActivity { activity -> result = runCatching { block(activity) } }
        return checkNotNull(result).getOrThrow()
    }

    private fun matchedContentWords(text: String): Int {
        val words = text.lowercase(Locale.ROOT).split(Regex("[^a-z]+")).toSet()
        return listOf("quick", "brown", "fox", "jumps", "lazy", "dog").count { it in words }
    }

    private companion object {
        const val TAG = "SpeechUiDemo"
        const val FIXED_SENTENCE = "The quick brown fox jumps over the lazy dog."
        val EXTERNAL_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
