# Android host

The launcher registers the microphone permission callback before STARTED and passes its platform adapter to the shared Compose screen. The manifest declares microphone access; no Internet permission is requested. The app packages the generated sherpa AAR and model assets through `composeApp`.

Run `./gradlew :androidApp:assembleDebug` from the root. Gradle prepares the pinned Android speech runtime and model assets. Supported ABIs: arm64-v8a and x86_64, API 26+. See [verification](../docs/development.md).

With a device connected, run `ANDROID_SERIAL=YOUR_SERIAL ./gradlew :androidApp:connectedDebugAndroidTest`. The native smoke test synthesizes a fixed sentence with Kitten and transcribes the resulting PCM with Whisper, checking for recognizable words. It uses the packaged model assets and JNI library without opening the microphone, requesting permission, or recording a person. The test logs the generated sample rate and recognized sentence under `NativeSpeechSmoke`.

For an explicit physical speaker-to-microphone test, unlock the phone, disconnect external audio devices, and place it in a quiet space. Run `ANDROID_SERIAL=YOUR_SERIAL ./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.acoustic=true`. This additionally launches the real application adapter, synthesizes a fixed sentence, records the device's speaker with `AudioRecord`, plays through `AudioTrack`, and transcribes the captured PCM. It grants this app microphone access if needed and temporarily sets media volume to half maximum, restoring volume afterward. It logs capture duration, PCM sample rate/count, RMS, peak, and recognized text under `AcousticSpeechSmoke`. The acoustic test is skipped without the explicit flag and never bypasses the device lock screen. Its assertion checks non-silent capture and at least four of six content words; it checks one generated sentence on physical hardware rather than measuring human speech recognition accuracy.

Verified on Pixel 9 / Android 17 on 2026-11-21: one test passed with no failures. Kitten generated 60,010 samples at 24,000 Hz, and Whisper returned “The quick brown fox jumps over the lazy dog.” See the [recorded result](verification/native-smoke-2026-11-21.json). This checks native model compatibility; it does not verify microphone recording or speaker playback. Gradle writes the machine-readable result under `androidApp/build/outputs/androidTest-results/connected/debug/`.

The separate [physical acoustic run](verification/acoustic-roundtrip-2026-11-21.json) also passed on that device: both tests completed with zero failures. The real microphone captured 58,878 samples at 16 kHz, and Whisper returned “The quick round fox jumps over the lazy dog.” Five of six content words matched. The evidence retains the earlier strict keyword failure and both exact transcripts; recognition was not word-perfect.

The host requests dark system-bar icons to preserve contrast with the shared light screen.

## Recent Changes

Added device verification for native model compatibility and the real microphone/speaker path, including recorded recognition errors and a readable light system-bar style.
