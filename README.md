# Siren — speech for Kotlin Multiplatform

Run **speech-to-text and text-to-speech on device** in Android, iOS and JVM apps. Siren handles compatible Whisper/Kitten model configuration, native inference and cleanup. The quick-start build setup supplies default models; your app owns audio input/output. Custom models remain configurable.

The core SDK has **no UI, Compose dependency or runtime downloads**. The quick-start plugin packages default models during the app build.

[![CI](https://github.com/Debanshu777/Siren/actions/workflows/check.yml/badge.svg)](https://github.com/Debanshu777/Siren/actions/workflows/check.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.debanshu777/siren)](https://central.sonatype.com/artifact/io.github.debanshu777/siren/0.1.0)
[![MIT license](https://img.shields.io/badge/source%20license-MIT-blue.svg)](LICENSE)

[Get started](#get-started) · [Configuration](docs/model-configuration.md) · [Samples](#sample-apps) · [Verification](docs/development.md)

<img src="docs/images/siren-demo.gif" width="320" alt="Actual Siren Android sample recording, transcribing and reading aloud">

*Actual Android sample. [Capture provenance and limits](docs/demo.md).*

## Get started

Siren SDK **0.1.0** and the [quick-start model add-on **0.1.0**](https://central.sonatype.com/artifact/io.github.debanshu777/siren-models-gradle-plugin/0.1.0) are available on Maven Central. The plugin adds the SDK dependency and packages both models by default; you can opt out below.

### 1. Use the quick-start build setup

In `settings.gradle.kts`, configure plugin and dependency repositories:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
```

Add this to the shared module's existing plugins:

```kotlin
plugins {
    id("io.github.debanshu777.siren.models") version "0.1.0"
}
```

This supplies the SDK dependency and both default model bundles. Ensure `mavenCentral()` is in your dependency repositories. Normal app builds fetch, verify and package Whisper tiny INT8 and Kitten nano INT8 automatically. No file placement, model-loading calls or extra initialization are needed. The installed app can use the models offline from first launch. Both bundles add about **138 MB before app compression**. Android uses the KMP Android library plugin; standard iOS Xcode direct integration copies app resources automatically. See the [integration details](siren-models-gradle-plugin/README.md#how-packaging-works) for custom iOS builds.

### 2. Call the engine

From a coroutine in `androidMain`, pass your app's mono recording and its actual sample rate:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use

val replyAudio = createSpeechEngine(context).use { engine ->
    val text = engine.transcribe(recording.samples, recording.sampleRate)
    engine.synthesize(text)
}
```

On iOS or desktop, use `createSpeechEngine()` instead. For text-to-speech alone, call `engine.synthesize("Hello from Siren.")` inside the scope. `use` closes automatically; play the independent returned samples at `replyAudio.sampleRate` through your app's audio adapter.

### Bring your own models

To keep the SDK setup and remove default-model packaging:

```kotlin
sirenModels { bundleModels.set(false) }
```

Supply your own bundle or local model configuration. You can also omit the plugin and add the [core dependency directly](docs/getting-started.md#add-the-dependency). Independent recognition/synthesis selection, SDK version overrides and iOS build integration are covered in the [quick-start guide](siren-models-gradle-plugin/README.md). Clean previous app build products once when changing packaging choices.

## Platform support

| Target | Runtime packaging | Observed verification |
| --- | --- | --- |
| Android API 27+ | arm64-v8a, x86_64 JNI | Pixel 9 native/physical audio/UI checks; x86_64 packaged, not device-tested |
| iOS | arm64 device/simulator static archives in KLIBs | Both dynamic consumer links; simulator app/native check. Physical iPhone audio unverified |
| JVM macOS arm64 | Native resources in JVM JAR | Native/relocated inference; packaged sample built and visually checked |
| JVM Linux x64 | Native resources in JVM JAR | Native model roundtrip passed in CI |
| JVM macOS x64, Linux arm64, Windows arm64/x64 | Native resources in JVM JAR | Packaged/hash-verified; execution untested |

The reference build uses Kotlin 2.4.21. The SDK targets Java 17 bytecode; the Gradle model plugin requires running Gradle on JDK 21 or later. Contributors use JDK 21. The pinned Compose sample uses iOS 18.5+. Wasm is unsupported. [Evidence and limits](docs/development.md).

## Sample apps

The separate [`sample/`](sample/README.md) adds a shared Compose screen, controller, microphone/playback adapters and model preparation:

```bash
./gradlew :sample:androidApp:assembleDebug
open sample/iosApp/iosApp.xcodeproj
./gradlew :sample:desktopApp:run
```

See [Android](sample/androidApp/README.md), [iOS](sample/iosApp/README.md) and [desktop](sample/desktopApp/README.md) setup. Desktop packaging needs a full JDK accepted by Compose/jpackage. SDK runtime preparation contains no weights; default sample builds prepare both bundles. Disabling a capability at runtime does not trim already packaged files.

## Scope

- Built-in Whisper adapters accept 0.2–20-second clips; the sample also limits TTS input to 500 characters.
- Scoped `use` handles cleanup. Long-lived engines have one lifecycle owner and must be closed when that owner finishes.
- Cancellation discards obsolete results but waits for synchronous native decoding before destruction.
- Custom descriptors/SPI remain available. Arbitrary ONNX graphs need compatible adapters.
- The sample is foreground-only; streaming, VAD, production model installation and automatic route recovery are outside it. Desktop hardware audio remains unverified.

## Documentation

[Getting started](docs/getting-started.md) · [Model configuration](docs/model-configuration.md) · [SDK](siren/README.md) · [Architecture](docs/architecture.md) · [Integration tutorial](docs/medium-draft.md) · [Development](docs/development.md) · [Release verification](docs/releasing.md)

## Recent Changes

Published the [model add-on 0.1.0](https://github.com/Debanshu777/Siren/releases/tag/models-v0.1.0) with SDK/default-model setup and a whole-bundle opt-out. The [release checks](https://github.com/Debanshu777/Siren/actions/runs/45073444409) verified signed Central delivery, public plugin-ID resolution, native inference, Android/JVM packaging, both iOS links and Xcode resource copying. Core SDK 0.1.0 remains unchanged.

## License

First-party source is [MIT](LICENSE). Native components and model assets retain their own licenses; preserve [third-party notices and audit status](THIRD_PARTY_NOTICES.md).
