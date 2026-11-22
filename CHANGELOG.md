# Changelog

Release status: `0.1.0` is prepared locally and not published to Maven Central.

## [0.1.0]

- Introduces a UI-free Kotlin Multiplatform speech SDK for Android, iOS and JVM, with no Compose dependency, audio-device handling or bundled model weights.
- Adds independently optional recognition/synthesis providers, explicit Whisper/Kitten file configuration, local/bundled model descriptors and caller-owned PCM with real sample rates.
- Packages Android wrapper/JNI runtime payloads, iOS static runtime archives in cinterop KLIBs and six desktop native resource sets in the JVM artifact.
- Provides separate Android, iOS and desktop sample hosts with an explicit sample-only model preset and foreground capture/transcription/playback flows.
- Verifies native JVM inference on macOS arm64 and Linux x64 CI, relocated Maven consumer inference/Android packaging/both iOS dynamic links, and the recorded Android physical audio/UI journey. Four other desktop native classifiers and desktop hardware audio remain unverified.
- Documents built-in Whisper's 0.2–20-second input range, sample-only 500-character TTS limit and cancellation that waits for synchronous native inference before cleanup.
