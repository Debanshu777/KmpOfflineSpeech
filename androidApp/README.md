# Android host

The launcher registers the microphone permission callback before STARTED and passes its platform adapter to the shared Compose screen. The manifest declares microphone access; no Internet permission is requested. The app packages the generated sherpa AAR and model assets through `composeApp`.

Run `python3 scripts/setup.py --platform android`, then `./gradlew :androidApp:assembleDebug` from the root. Supported ABIs: arm64-v8a and x86_64, API 26+. See [verification](../docs/development.md).

With a device connected, run `ANDROID_SERIAL=YOUR_SERIAL ./gradlew :androidApp:connectedDebugAndroidTest`. The native smoke test synthesizes a fixed sentence with Kitten and transcribes the resulting PCM with Whisper, checking for recognizable words. It uses the packaged model assets and JNI library without opening the microphone, requesting permission, or recording a person. The test logs the generated sample rate and recognized sentence under `NativeSpeechSmoke`.

Verified on Pixel 9 / Android 17 on 2026-11-21: one test passed with no failures. Kitten generated 60,010 samples at 24,000 Hz, and Whisper returned “The quick brown fox jumps over the lazy dog.” See the [recorded result](verification/native-smoke-2026-11-21.json). This checks native model compatibility; it does not verify microphone recording or speaker playback. Gradle writes the machine-readable result under `androidApp/build/outputs/androidTest-results/connected/debug/`.

## Recent Changes

Added the minimal Android host and a device smoke test for the packaged Kitten-to-Whisper speech path.
