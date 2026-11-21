package dev.debanshu.speech.android

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.debanshu.speech.PcmAudio
import dev.debanshu.speech.SpeechPlatform
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/** Opt-in physical speaker -> air -> microphone test. It intentionally emits a fixed sentence. */
@RunWith(AndroidJUnit4::class)
class AcousticSpeechRoundTripTest {
    @Test(timeout = 120_000)
    fun productionCaptureAndPlaybackCanRoundTripSpeech() {
        assumeTrue(
            "Opt in with -Pandroid.testInstrumentationRunnerArguments.acoustic=true on an unlocked, quiet device.",
            InstrumentationRegistry.getArguments().getString("acoustic") == "true",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assertTrue("Unlock the device before the acoustic test. The test never bypasses the lock screen.", !keyguard.isDeviceLocked)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val externalOutputs = outputs.filter { it.type in EXTERNAL_OUTPUT_TYPES }
        assertTrue("Disconnect headphones, Bluetooth, HDMI, and USB audio before the acoustic test.", externalOutputs.isEmpty())

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        }
        val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val testVolume = maxOf(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2)
        try {
            setMediaVolume(audioManager, testVolume)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var platform: SpeechPlatform
                scenario.onActivity { platform = it.speech }
                runBlocking {
                    var spoken: PcmAudio? = null
                    var recorded: PcmAudio? = null
                    try {
                        // Loading and generation happen first so they cannot consume the recording cap.
                        spoken = platform.synthesize("The quick brown fox jumps over the lazy dog.")
                        assertTrue("The microphone permission must be granted for this opt-in test.", platform.requestMicrophonePermission())
                        platform.startRecording()
                        delay(500)
                        platform.play(spoken)
                        delay(700)
                        recorded = platform.stopRecording()
                        val rms = sqrt(recorded.samples.sumOf { it.toDouble() * it } / recorded.samples.size)
                        val peak = recorded.samples.maxOf { abs(it) }
                        val duration = recorded.samples.size.toDouble() / recorded.sampleRate
                        assertTrue("Capture was too short: ${duration}s", duration >= 1.0)
                        assertTrue("Microphone PCM was silent: rms=$rms, peak=$peak", rms > 0.0005 && peak > 0.005f)
                        val transcript = platform.transcribe(recorded)
                        val normalized = transcript.lowercase(Locale.ROOT)
                        val contentWords = listOf("quick", "brown", "fox", "jumps", "lazy", "dog")
                        val recognizedWords = contentWords.filter { Regex("\\b$it\\b").containsMatchIn(normalized) }
                        Log.i(
                            TAG,
                            "ttsRate=${spoken.sampleRate}; ttsSamples=${spoken.samples.size}; " +
                                "captureRate=${recorded.sampleRate}; captureSamples=${recorded.samples.size}; " +
                                "durationSeconds=$duration; rms=$rms; peak=$peak; " +
                                "mediaVolume=$testVolume; outputTypes=${outputs.map { it.type }}; " +
                                "matchedContentWords=${recognizedWords.size}/${contentWords.size}; transcript=$transcript",
                        )
                        // A hardware-path smoke test tolerates recognition errors. It is not an accuracy benchmark.
                        assertTrue(
                            "Expected at least four content words after physical speaker-to-microphone capture: $transcript",
                            recognizedWords.size >= 4,
                        )
                    } finally {
                        platform.stop()
                        spoken?.samples?.fill(0f)
                        recorded?.samples?.fill(0f)
                    }
                }
            }
        } finally {
            setMediaVolume(audioManager, originalVolume)
        }
    }

    private fun setMediaVolume(manager: AudioManager, volume: Int) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Only the test temporarily adopts this permission; the application manifest does not gain it.
        automation.adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private companion object {
        const val TAG = "AcousticSpeechSmoke"
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
