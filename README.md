# Siren — speech for Kotlin Multiplatform

Run **speech-to-text and text-to-speech on device** in Android, iOS and JVM applications. Siren provides a shared PCM API and replaceable model providers, with built-in [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) adapters for compatible Whisper and Kitten exports.

The SDK contains **no UI, Compose dependency or model weights**. Your application owns model installation, microphone access and playback. Recognition and synthesis can be used independently.

[![CI](https://github.com/Debanshu777/Siren/actions/workflows/check.yml/badge.svg)](https://github.com/Debanshu777/Siren/actions/workflows/check.yml)
[![MIT license](https://img.shields.io/badge/source%20license-MIT-blue.svg)](LICENSE)

[Get started](#get-started) · [Model configuration](docs/model-configuration.md) · [Sample apps](#sample-apps) · [Verification](docs/development.md)

<img src="docs/images/siren-demo.gif" width="320" alt="Actual Siren Android sample recording, transcribing and reading aloud">

*Actual Android sample. [Capture provenance and limits](docs/demo.md).*

## Get started

**Release status: `0.1.0` is prepared locally and has not been published to Maven Central.** The dependency below is the intended future release coordinate and will not resolve from Central until publication. Use the source/local staging meanwhile. See [release preparation and remaining gates](docs/releasing.md).

Add `mavenCentral()` to your repositories and Siren to your existing KMP shared module:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.debanshu777:siren:0.1.0")
        }
    }
}
```

Native runtime binaries are supplied by the selected SDK artifact. Model weights are separate: obtain a compatible model bundle and retain all supporting files. This synthesis-only example expects an absolute directory containing the Kitten model, voices, tokens and English lexicon:

```kotlin
import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels

fun voiceModels(directory: String): SherpaModels = SherpaModels(
    synthesis = KittenModel(
        model = ModelFile.Local("$directory/model.int8.onnx"),
        voices = ModelFile.Local("$directory/voices.bin"),
        tokens = ModelFile.Local("$directory/tokens.txt"),
        lexicon = ModelFile.Local("$directory/lexicon-en-us.txt"),
    ),
)
```

Create the engine in the appropriate platform source set:

```kotlin
// androidMain
import android.content.Context
import io.github.debanshu.siren.speech.createAndroidSpeechEngine

fun androidEngine(context: Context, directory: String) =
    createAndroidSpeechEngine(context.applicationContext, voiceModels(directory))
```

```kotlin
// iosMain
import io.github.debanshu.siren.speech.createIosSpeechEngine

fun iosEngine(directory: String) = createIosSpeechEngine(voiceModels(directory))
```

```kotlin
// jvmMain
import io.github.debanshu.siren.speech.createJvmSpeechEngine

fun desktopEngine(directory: String) = createJvmSpeechEngine(voiceModels(directory))
```

Shared code can generate caller-owned audio and release the engine:

```kotlin
import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.SpeechEngine

suspend fun renderGreeting(engine: SpeechEngine): PcmAudio = try {
    engine.synthesize("Hello from Kotlin Multiplatform.")
} finally {
    engine.close()
}
```

Play the returned mono float samples at `sampleRate` using your application's audio output. Siren does not play audio itself. Add a `WhisperModel` to enable `engine.transcribe(PcmAudio(...))`, or supply your own `SpeechRecognizer`/`SpeechSynthesizer`. The [complete getting-started guide](docs/getting-started.md) covers both models, bundled resources, file acquisition and lifecycle ownership.

## Platform support

| Target | Runtime packaging | Observed verification |
| --- | --- | --- |
| Android API 26+ | arm64-v8a, x86_64 JNI | Pixel 9 native/configuration and physical audio/UI checks; x86_64 packaged, not device-tested |
| iOS | arm64 device and simulator static archives in KLIBs | Both dynamic consumer links; simulator app/native check. Physical iPhone audio unverified |
| JVM macOS arm64 | Native resources in JVM JAR | SDK/relocated consumer inference; packaged sample built, launched and visually checked |
| JVM Linux x64 | Native resources in JVM JAR | Native model roundtrip passed in CI |
| JVM macOS x64, Linux arm64, Windows arm64/x64 | Native resources in JVM JAR | Packaged/hash-verified; execution untested |

The reference build uses Kotlin 2.4.21 and Java 17 bytecode; contributor builds use JDK 21. The pinned Compose sample uses iOS 18.5+. This is not a universal minimum for native speech runtimes. Other toolchains and Wasm are not verified/supported. [Evidence and precise limits](docs/development.md).

## Sample apps

The separate [`sample/`](sample/README.md) supplies a shared Compose screen, controller, microphone/playback adapters and an explicit Whisper tiny/Kitten nano preset:

```bash
./gradlew :sample:androidApp:assembleDebug
open sample/iosApp/iosApp.xcodeproj
./gradlew :sample:desktopApp:run
```

Android needs the SDK listed in the [sample instructions](sample/androidApp/README.md). iOS needs full Xcode on Apple Silicon; physical devices need signing. Desktop packaging needs a full JDK accepted by Compose/jpackage. The [desktop guide](sample/desktopApp/README.md) covers application images and OS permissions.

Sample builds prepare their weights through Gradle. SDK runtime preparation and published artifacts contain no weights or demo preset. Setting a runtime capability to null does not remove native binaries or sample assets already packaged by a build.

## Design and limits

- UI-free `:siren` SDK; application concerns stay under `sample/`.
- Explicit `SherpaModels` selects compatible files/settings. Arbitrary ONNX graphs need their own adapters.
- Built-in Whisper adapters accept 0.2–20-second clips. The sample also caps TTS input at 500 characters.
- The engine serializes operations and close. Cancellation discards obsolete results but waits for synchronous native work before destruction.
- Sessions are created per inference call. Streaming, VAD, production model installation, background audio and automatic route recovery are outside the sample.
- Desktop microphone/speaker hardware remains unverified; a model-only roundtrip is not an audio-hardware or accuracy benchmark.

## Documentation and development

- [Getting started](docs/getting-started.md) · [Model configuration](docs/model-configuration.md)
- [SDK module](siren/README.md) · [Architecture](docs/architecture.md)
- [Integration tutorial](docs/medium-draft.md) · [Actual captures](docs/demo.md)
- [Development and CI](docs/development.md) · [Release preparation](docs/releasing.md) · [Distribution design](docs/library-design.md)

The [relocated consumer](validation/published-consumer-verification.json) resolves copied Maven artifacts outside the SDK checkout and verifies JVM native inference, Android packaging and both iOS dynamic links. [Build preparation](buildSrc/README.md) is entirely Gradle/Kotlin.

## Recent Changes

Prepared `0.1.0` locally with explicit platform APIs, native runtimes, sample apps and release validation. Publication is deferred; native redistribution readiness and credential setup remain required before a future release.

## License

First-party source is [MIT](LICENSE). Bundled sherpa-onnx components use Apache-2.0; other native dependencies and model assets retain their respective terms. Preserve [third-party licenses and notices](THIRD_PARTY_NOTICES.md) when redistributing runtime binaries or weights.
