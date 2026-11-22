# Siren SDK

`:siren` supplies UI-free speech inference for Android, iOS and JVM. Its package is `io.github.debanshu.siren`, with explicit API mode and no Compose dependency, model weights, microphone handling or playback implementation.

Siren **0.1.0** is available on [Maven Central](https://central.sonatype.com/artifact/io.github.debanshu777/siren/0.1.0). Add this to your KMP module's `commonMain.dependencies` with `mavenCentral()` configured:

```kotlin
implementation("io.github.debanshu777:siren:0.1.0")
```

See [getting started](../docs/getting-started.md) for model-bundle placement and a recording-to-speech example.

## Basic use

Put the known Whisper tiny INT8/Kitten nano INT8 bundle under your application's `models/` resources. Siren knows its filenames through `SherpaModels.bundled()`; no path-by-path configuration is required.

The recommended source-only [quick-start Gradle helper](../docs/optional-models.md) supplies the SDK dependency and packages those default files automatically; `bundleModels.set(false)` opts out of model packaging. It is an unpublished add-on, separate from the public Siren 0.1.0 artifact.

In an Android coroutine:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.use

val audio = createSpeechEngine(context).use { engine ->
    engine.synthesize("Hello from Siren.")
}
```

On iOS/JVM use `createSpeechEngine()` without Android context. `use` closes automatically, including after errors/cancellation, and returns independent `PcmAudio`. Your app plays its mono float samples at the returned rate. With the `speech.transcribe` extension imported, `engine.transcribe(samples, sampleRate)` accepts application-recorded PCM directly.

Defaults add only file-layout metadata, not weights, loading or network access. Applications must supply the models. Files/native sessions open when the relevant operation runs.

## Optional configuration and providers

```kotlin
import io.github.debanshu.siren.speech.SherpaModels

val local = SherpaModels.local("/absolute/path/to/models")
val voiceOnly = SherpaModels.bundled(recognition = false)
val differentFolder = SherpaModels.bundled(directory = "speech-models")
```

Pass an override as the factory's `models` argument. Both helpers map the standard `whisper/`/`kitten/` folders. Typed `WhisperModel`, `KittenModel` and `ModelFile` constructors remain for custom compatible exports. `SpeechRecognizer`/`SpeechSynthesizer` and `ModelSpeechEngine` remain the extension point for other model families. See [configuration](../docs/model-configuration.md).

The original `createAndroidSpeechEngine`, `createIosSpeechEngine` and `createJvmSpeechEngine` names are preserved and also default to the bundled layout. No sample module is required. `SampleModels.bundledDemo()` is only a sample wrapper.

`use` is for a scope that should finish and close. Long-lived UI/audio owners can keep an engine across actions and close it on disposal. Calls/teardown are serialized; native decoding may finish before cancellation cleanup. JVM classpath model copies are engine-owned and removed on close; Local files stay caller-owned.

## Runtime artifacts

| Target | SDK payload |
| --- | --- |
| Android | Wrapper classes, arm64-v8a/x86_64 JNI, notices and consumer keep rules |
| iOS | sherpa/ONNX static archives in target-specific cinterop KLIBs |
| JVM | Java wrapper and six native resource sets; upstream OS/architecture selection |

SDK artifacts contain no model weights, UI or hardware audio. `prepareAndroidRuntime`, `prepareIosSpeech` and `prepareJvmRuntime` prepare runtimes only. Native consumers require no SDK-checkout linker paths.

JVM payloads cover macOS/Linux/Windows arm64 and x64. Inference is verified on [macOS arm64](verification/jvm-native-macos-arm64-2026-11-22.json) and [Linux x64 CI](verification/jvm-native-linux-x64-2026-11-22.json); the other four are packaged/hash-verified but execution-untested. Android starts at API 27; the pinned iOS sample uses 18.5. Desktop physical audio remains unverified. See [development evidence](../docs/development.md).

## Source checks

```bash
./gradlew :siren:jvmTest :siren:checkKotlinAbi
./gradlew :siren:publishAllPublicationsToStagingRepository
```

Local staging writes `build/staging-repository`, not Central. For opt-in native JVM testing, prepare weights separately with `prepareSpeechModels`, then use `:siren:jvmTest -Pnative=true -PmodelsDir="$(pwd)/models"`. Default SDK tests do not load models.

First-party source is [MIT](../LICENSE); preserve [native/model notices and audit status](../THIRD_PARTY_NOTICES.md). [Release verification](../docs/releasing.md) records public delivery and Central-only consumer checks.

## Recent Changes

Documented default-ready SDK/model setup and the whole-bundle opt-out in the unpublished build helper. Core API and published runtime artifacts are unchanged.

Published 0.1.0 to Maven Central; independent Central-only consumers verify JVM native inference, Android packaging and both iOS dynamic links.

Added simple factories, model-layout defaults, scoped close and direct PCM calls. SDK artifacts retain 33 pinned native/compiler notice texts and source availability; Android minimum 27 matches the pinned runtime build.
