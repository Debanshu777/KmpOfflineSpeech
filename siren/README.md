# Siren SDK

`:siren` is the UI-free inference module for `io.github.debanshu777:siren`. Its Kotlin packages are `io.github.debanshu.siren.audio` and `.speech`, built with explicit API mode. It has no Compose dependency, UI/components, hardware audio, microphone permissions or model weights.

**Release status:** candidate `0.1.0` is prepared locally and is not published to Maven Central. The intended future shared dependency is:

```kotlin
implementation("io.github.debanshu777:siren:0.1.0")
```

After publication, use `mavenCentral()` in your repositories. Until then, build from source or use a local staged repository. See [getting started](../docs/getting-started.md) for platform initialization, model acquisition and PCM examples.

## Public API

| Type | Responsibility |
| --- | --- |
| `PcmAudio` | Caller-owned mono float samples plus their actual sample rate |
| `SpeechRecognizer`, `SpeechSynthesizer` | Independently replaceable providers with labels and cleanup |
| `ModelSpeechEngine` | Optional capabilities, serialized provider calls and close |
| `SherpaModels`, `WhisperModel`, `KittenModel` | Explicit compatible-export configuration for built-in adapters |
| `ModelFile.Bundled`, `ModelFile.Local` | Application resources or absolute filesystem paths |

Create an engine with `createAndroidSpeechEngine(context, models)`, `createIosSpeechEngine(models)` or `createJvmSpeechEngine(models, classLoader)`. Every factory requires explicit models; the JVM class loader is optional. Native sessions/files open lazily when an operation runs. Factories do not download weights or operate audio devices.

The demo preset `SampleModels.bundledDemo()` lives only in [`sample/`](../sample/README.md). A consumer can configure just one capability or implement another concrete model adapter. Changing an ONNX filename does not make an unrelated graph compatible with Whisper/Kitten. See [model configuration](../docs/model-configuration.md).

The engine owns its providers. Close it before replacement or disposal; inference and close are serialized. Synchronous native decoding may finish before cancellation cleanup can complete. Returned PCM is independent of native session lifetime. JVM bundled weights are materialized into engine-owned temporary files and removed on close; Local paths stay caller-owned.

## Platform artifacts

- **Android:** verified upstream wrapper classes, arm64-v8a/x86_64 JNI libraries, notices/provenance and consumer keep rules are embedded in the SDK AAR. No model assets or UI resources are included.
- **iOS:** target-specific cinterop KLIBs embed sherpa and ONNX Runtime static archives with required Apple system-library linkage. Consumers do not need SDK-checkout `-F` paths.
- **JVM:** the JAR embeds the Java wrapper and six `sherpa-onnx/native/<classifier>` resource sets. Upstream loading chooses OS/architecture and loads ONNX before JNI. No native checkout override is needed.

JVM classifiers are `osx-aarch64`, `osx-x64`, `linux-aarch64`, `linux-x64`, `win-arm64` and `win-x64`. Native inference is verified on [macOS arm64](verification/jvm-native-macos-arm64-2026-11-22.json) and [Linux x64 CI](verification/jvm-native-linux-x64-2026-11-22.json); the other four payloads are packaged/hash-verified but execution-untested. Android's floor is API 26. The sample's pinned iOS baseline is 18.5. Wasm is unsupported.

Hardware capture/playback stays in the application. The desktop sample was packaged/launched, but desktop microphone/speaker behavior is unverified. See [development evidence](../docs/development.md) for precise target/toolchain scope.

## Build and validate from source

From the repository root:

```bash
./gradlew :siren:jvmTest
./gradlew :siren:compileKotlinIosSimulatorArm64
./gradlew :siren:publishAllPublicationsToStagingRepository
```

The staging task writes a file Maven repository under `build/staging-repository`; it does not publish remotely. Version comes from root `gradle.properties`.

Native JVM verification is opt-in and requires separate compatible weights:

```bash
./gradlew prepareSpeechModels
./gradlew :siren:jvmTest -Pnative=true -PmodelsDir="$(pwd)/models"
```

Default SDK tests and runtime-only preparation do not obtain model weights. The [relocated consumer](../validation/published-consumer-verification.json) proves native JVM inference, plain Android APK packaging and both iOS dynamic links through Maven coordinates outside this checkout. Release candidates must rerun these checks against their own artifacts.

## Licensing and releases

SDK source is [MIT](../LICENSE). Upstream native components, including Apache-2.0 sherpa-onnx code, carry separate redistribution notices; model weights have their own terms. Preserve [third-party notices](../THIRD_PARTY_NOTICES.md). See [release workflow and holds](../docs/releasing.md); local staging, Central validation and Central availability are distinct states.

## Recent Changes

Prepared local `0.1.0` release documentation, explicit platform factories and native payload guidance. Public publication remains deferred and subject to native redistribution readiness.
