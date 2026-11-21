package dev.debanshu.speech.android

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.debanshu.speech.PcmAudio
import dev.debanshu.speech.SpeechPlatform
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in real UI journey. The fixture emits a sentence; it never inserts a transcript or UI state. */
@RunWith(AndroidJUnit4::class)
class SpeechUiDemoTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val observedStatuses = linkedSetOf<String>()

    @Test(timeout = 120_000)
    fun recordTranscribeAndSpeakThroughTheRealScreen() {
        assumeTrue(
            "Opt in with -Pandroid.testInstrumentationRunnerArguments.demo=true on an unlocked device.",
            InstrumentationRegistry.getArguments().getString("demo") == "true",
        )
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assertTrue("Unlock the phone before running this demo. The test never bypasses the lock screen.", !keyguard.isDeviceLocked)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertTrue(
            "Disconnect external audio devices before the speaker-to-microphone demo.",
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in EXTERNAL_OUTPUT_TYPES },
        )
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        }
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val originalAccessibilityFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            setMediaVolume(audioManager, maxOf(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var platform: SpeechPlatform
                scenario.onActivity { platform = it.speech }
                var stimulus: PcmAudio? = null
                try {
                    // Prepare before UI recording begins. This is only the physical acoustic stimulus.
                    val preparedStimulus = runBlocking { platform.synthesize(FIXED_SENTENCE) }
                    stimulus = preparedStimulus
                    awaitStatus("Ready", 20_000)
                    Log.i(TAG, "DEMO_VISIBLE")
                    Thread.sleep(1_000)
                    clickTaggedControl("record_control")
                    awaitStatus("Listening", 10_000)
                    Thread.sleep(600)
                    // The real shared controller still owns microphone capture and the Listening UI.
                    runBlocking { platform.play(preparedStimulus) }
                    Thread.sleep(700)
                    clickTaggedControl("record_control")
                    val transcript = awaitGenuineTranscript(45_000)
                    Log.i(TAG, "RECOGNIZED_TRANSCRIPT=$transcript; contentWords=${matchedContentWords(transcript)}/6; characters=${transcript.length}")

                    // Drive the real controller's synthesis and playback, rather than the fixture.
                    clickTaggedControl("play_control")
                    awaitPlaybackAndReturnToReady(45_000)
                    Log.i(TAG, "transcript=$transcript; contentWords=${matchedContentWords(transcript)}/6; observedStatuses=$observedStatuses")
                    Thread.sleep(2_000) // Keep the genuine final Ready screen visible in a demo capture.
                } finally {
                    runBlocking { platform.stop() }
                    stimulus?.samples?.fill(0f)
                }
            }
        } finally {
            setMediaVolume(audioManager, originalVolume)
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalAccessibilityFlags }
        }
    }

    private fun awaitStatus(expected: String, timeoutMillis: Long) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            if (lastStatus == expected) return
            Thread.sleep(150)
        }
        error("Expected UI status '$expected'; last status '$lastStatus'; observed $observedStatuses; ${accessibilitySummary()}")
    }

    private fun awaitGenuineTranscript(timeoutMillis: Long): String {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var lastTranscript = ""
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            lastTranscript = readTaggedText("transcript_text")
            if (lastStatus == "Ready" && matchedContentWords(lastTranscript) >= 4) return lastTranscript
            Thread.sleep(150)
        }
        error("The UI did not show a genuine recognized sentence. Status='$lastStatus'; transcript='$lastTranscript'; observed=$observedStatuses; ${accessibilitySummary()}")
    }

    private fun awaitPlaybackAndReturnToReady(timeoutMillis: Long) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        var sawPlaybackWork = false
        var lastStatus = ""
        while (System.nanoTime() < deadline) {
            lastStatus = readStatus()
            if (lastStatus == "Generating speech" || lastStatus == "Speaking") sawPlaybackWork = true
            if (sawPlaybackWork && lastStatus == "Ready") return
            Thread.sleep(150)
        }
        error("UI playback did not complete. Status='$lastStatus'; saw work=$sawPlaybackWork; observed=$observedStatuses; ${accessibilitySummary()}")
    }

    private fun readStatus(): String = readTaggedText("speech_status").also {
        if (it.isNotBlank() && observedStatuses.add(it)) Log.i(TAG, "UI_STATUS=$it")
    }

    private fun readTaggedText(id: String): String {
        val root = freshRoot() ?: return ""
        return try {
            val nodes = findTaggedNodes(root, id)
            try {
                nodes.firstOrNull { it.isVisibleToUser }?.let(::readNodeText).orEmpty()
            } finally {
                nodes.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    private fun readNodeText(node: AccessibilityNodeInfo): String {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val parts = mutableListOf<String>()
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                readNodeText(child).takeIf { it.isNotBlank() }?.let(parts::add)
            } finally {
                child.recycle()
            }
        }
        return parts.joinToString(" ").trim()
    }

    private fun clickTaggedControl(id: String) {
        val root = freshRoot() ?: error("The application window is not active")
        try {
            val nodes = findTaggedNodes(root, id)
            try {
                check(nodes.any { it.isVisibleToUser && clickEnabledAncestor(it) }) {
                    "Could not click visible enabled control '$id'; ${accessibilitySummary()}"
                }
            } finally {
                nodes.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    private fun findTaggedNodes(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> {
        val resourceMatches = root.findAccessibilityNodeInfosByViewId(id)
        if (resourceMatches.any { it.isVisibleToUser }) return resourceMatches
        resourceMatches.forEach { it.recycle() }
        val tagMatches = collectNodes(root) {
            it.isVisibleToUser && it.extras.getString("androidx.compose.ui.semantics.testTag") == id
        }
        if (tagMatches.isNotEmpty()) return tagMatches
        // Visible labels and the editable-text class are portable when test metadata is not exported.
        return collectNodes(root) { node ->
            if (!node.isVisibleToUser) false else when (id) {
                "speech_status" -> node.text?.toString()?.trim() in STATUS_LABELS
                "transcript_text" -> node.className?.toString() == "android.widget.EditText"
                "record_control" -> node.contentDescription?.toString()?.trim() in RECORD_LABELS ||
                    node.text?.toString()?.trim() in RECORD_LABELS
                "play_control" -> node.text?.toString()?.trim() in PLAY_LABELS ||
                    node.contentDescription?.toString()?.trim() in PLAY_LABELS
                else -> false
            }
        }
    }

    private fun collectNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo> {
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (predicate(node)) {
                // The caller owns these copies; traversal separately releases each child it opens.
                matches += AccessibilityNodeInfo.obtain(node)
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child)
                } finally {
                    child.recycle()
                }
            }
        }
        visit(root)
        return matches
    }

    private fun clickEnabledAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        try {
            while (current != null) {
                val candidate = current
                if (candidate.isVisibleToUser && candidate.isEnabled && candidate.isClickable &&
                    candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ) return true
                current = candidate.parent
                candidate.recycle()
            }
            return false
        } finally {
            current?.recycle()
        }
    }

    private fun accessibilitySummary(): String {
        val root = freshRoot() ?: return "activeWindow=null"
        try {
            val packageName = root.packageName
            val visibleLabels = mutableListOf<String>()
            fun visit(node: AccessibilityNodeInfo) {
                if (visibleLabels.size >= 16) return
                if (node.isVisibleToUser) {
                    val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                        ?: node.contentDescription?.toString()
                    label?.takeIf { it.isNotBlank() }?.let { visibleLabels += it.take(60) }
                }
                for (index in 0 until node.childCount) {
                    if (visibleLabels.size >= 16) break
                    val child = node.getChild(index) ?: continue
                    try { visit(child) } finally { child.recycle() }
                }
            }
            visit(root)
            return "activePackage=$packageName; visibleLabels=$visibleLabels"
        } finally {
            root.recycle()
        }
    }

    private fun freshRoot(): AccessibilityNodeInfo? {
        // UiAutomation.clearCache was added in API 34. Compose's exported nodes can otherwise
        // retain old text after the actual screen and native playback have already advanced.
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return null
        if (Build.VERSION.SDK_INT < 34 && !root.refresh()) {
            root.recycle()
            return null
        }
        return root
    }

    private fun matchedContentWords(text: String): Int {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf("quick", "brown", "fox", "jumps", "lazy", "dog")
            .count { Regex("\\b$it\\b").containsMatchIn(normalized) }
    }

    private fun setMediaVolume(manager: AudioManager, volume: Int) {
        automation.adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private companion object {
        const val TAG = "SpeechUiDemo"
        const val FIXED_SENTENCE = "The quick brown fox jumps over the lazy dog."
        val STATUS_LABELS = setOf("Ready", "Opening microphone", "Listening", "Transcribing", "Generating speech", "Speaking", "Stopping")
        val RECORD_LABELS = setOf("Record", "Stop & transcribe")
        val PLAY_LABELS = setOf("Speak", "Stop")
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
