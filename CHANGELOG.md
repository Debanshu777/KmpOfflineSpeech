# Changelog

Release status: [0.1.0](https://github.com/Debanshu777/Siren/releases/tag/v0.1.0) is published to Maven Central.

## [0.1.0]

- Introduces a UI-free Kotlin Multiplatform speech SDK for Android, iOS and JVM, with no Compose dependency, audio-device handling or bundled model weights.
- Adds independently optional recognition/synthesis providers, explicit Whisper/Kitten file configuration, local/bundled model descriptors and caller-owned PCM with real sample rates.
- Simplifies standard-bundle integration with platform `createSpeechEngine` factories, bundled/local layout helpers, scoped automatic close and direct FloatArray/sample-rate transcription; original named factories and custom model APIs remain available.
- Packages Android wrapper/JNI runtime payloads, iOS static runtime archives in cinterop KLIBs and six desktop native resource sets in the JVM artifact.
- Provides separate Android, iOS and desktop sample hosts with a sample wrapper around the known bundle layout and foreground capture/transcription/playback flows. The SDK helper adds no weights or downloader.
- Verifies native JVM inference on macOS arm64 and Linux x64 CI, relocated Maven consumer inference/Android packaging/both iOS dynamic links, and the recorded Android physical audio/UI journey. Four other desktop native classifiers and desktop hardware audio remain unverified.
- Documents built-in Whisper's 0.2–20-second input range, sample-only 500-character TTS limit and cancellation that waits for synchronous native inference before cleanup.

- Retains 33 pinned native/compiler notice texts, corresponding source availability and exact compiler/runtime provenance; verifies vendored text and evidence hashes before any Central upload.
- Adds read-only Central authentication/namespace checks and a release workflow that defaults to verification-only, including local signing of all target candidates.
- Sets Android minimum SDK 27 consistently with the pinned ONNX Runtime native build.
- Keeps relocated SDK resolution isolated while allowing missing build-tool dependencies on fresh CI runners; explicit offline mode remains supported.
