# Siren — speech for Kotlin Multiplatform

Run **speech-to-text and text-to-speech on device** in Android, iOS and JVM apps. Siren handles compatible Whisper/Kitten model configuration, native inference and cleanup. Your app supplies the model files and audio input/output.

The SDK has **no UI, Compose dependency, bundled weights or hidden downloads**.

[![CI](https://github.com/Debanshu777/Siren/actions/workflows/check.yml/badge.svg)](https://github.com/Debanshu777/Siren/actions/workflows/check.yml)
[![MIT license](https://img.shields.io/badge/source%20license-MIT-blue.svg)](LICENSE)

[Get started](#get-started) · [Configuration](docs/model-configuration.md) · [Samples](#sample-apps) · [Verification](docs/development.md)

<img src="docs/images/siren-demo.gif" width="320" alt="Actual Siren Android sample recording, transcribing and reading aloud">

*Actual Android sample. [Capture provenance and limits](docs/demo.md).*

## Get started

**`0.1.0` is prepared locally, not published to Maven Central.** The dependency below is the intended future coordinate. Use the source/local staging until publication; see [release preparation](docs/releasing.md).

### 1. Add the dependency

With `mavenCentral()` configured, add to your KMP shared module:

```kotlin
commonMain.dependencies {
    implementation("io.github.debanshu777:siren:0.1.0")
}
```

### 2. Put the model bundle in your app

Use the [standard bundle](docs/getting-started.md#place-the-model-bundle): Whisper tiny INT8 and Kitten nano INT8 files under `models/whisper/` and `models/kitten/`. Android reads assets, iOS reads application resources, and JVM reads classpath resources. Siren already knows the seven filenames; you do not map them individually.

The SDK does not include or download these weights. For an existing local bundle, use the [one-line local-directory override](docs/model-configuration.md#change-the-directory-or-capability).

### 3. Create an engine and call it

From a coroutine in `androidMain`:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.use

val audio = createSpeechEngine(context).use { engine ->
    engine.synthesize("Hello from Siren.")
}
```

From `iosMain` or `jvmMain`, the same operation needs no Android context:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.use

val audio = createSpeechEngine().use { engine ->
    engine.synthesize("Hello from Siren.")
}
```

`use` closes the engine automatically, including after failure or cancellation. The returned `PcmAudio` contains caller-owned mono float samples and their playback rate.

For speech-to-text, pass your application's recorded PCM at its actual rate. For example, inside the same engine scope:

```kotlin
import io.github.debanshu.siren.speech.transcribe

val text = engine.transcribe(recordedSamples, recordedSampleRate)
val spoken = engine.synthesize(text)
```

Your app still records and plays audio. See [the complete quickstart](docs/getting-started.md) for resource placement, both operations and longer-lived engines. Custom filenames, optional capabilities and other model providers are covered in [advanced configuration](docs/model-configuration.md).

## Platform support

| Target | Runtime packaging | Observed verification |
| --- | --- | --- |
| Android API 26+ | arm64-v8a, x86_64 JNI | Pixel 9 native/physical audio/UI checks; x86_64 packaged, not device-tested |
| iOS | arm64 device/simulator static archives in KLIBs | Both dynamic consumer links; simulator app/native check. Physical iPhone audio unverified |
| JVM macOS arm64 | Native resources in JVM JAR | Native/relocated inference; packaged sample built and visually checked |
| JVM Linux x64 | Native resources in JVM JAR | Native model roundtrip passed in CI |
| JVM macOS x64, Linux arm64, Windows arm64/x64 | Native resources in JVM JAR | Packaged/hash-verified; execution untested |

The reference build uses Kotlin 2.4.21 and Java 17 bytecode; contributors use JDK 21. The pinned Compose sample uses iOS 18.5+. Wasm is unsupported. [Evidence and limits](docs/development.md).

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

[Getting started](docs/getting-started.md) · [Model configuration](docs/model-configuration.md) · [SDK](siren/README.md) · [Architecture](docs/architecture.md) · [Integration tutorial](docs/medium-draft.md) · [Development](docs/development.md) · [Release preparation](docs/releasing.md)

## Recent Changes

Added one-line platform engine creation, bundled/local layout helpers, automatic scoped cleanup and direct PCM transcription. Custom configuration remains optional; `0.1.0` stays local and unpublished.

Added a manual [signing-secret CI check](validation/signing-check/README.md) with explicit protected/unprotected key modes. It signs a harmless probe with Gradle and verifies it against the published public key. It runs no SDK publication, release or tag commands. Maven credential presence is checked; authentication is not tested.

## License

First-party source is [MIT](LICENSE). Native components and model assets retain their own licenses; preserve [third-party notices and audit status](THIRD_PARTY_NOTICES.md).
