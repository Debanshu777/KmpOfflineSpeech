# Siren SDK

`:siren` is the UI-free inference library. It uses explicit API mode and packages `io.github.debanshu.siren.audio` and `.speech`. It depends on coroutines, with no Compose dependency, UI components, hardware audio, permission handling or model weights.

## API and configuration

- `PcmAudio`: caller-owned mono float samples with their actual sample rate.
- `SpeechRecognizer` and `SpeechSynthesizer`: independently replaceable providers with display labels and cleanup.
- `ModelSpeechEngine`: serializes provider work/close and exposes optional capabilities.
- `SherpaModels`, `WhisperModel`, `KittenModel`, `ModelFile.Bundled`/`Local`: explicit configuration for the built-in sherpa adapters.

Android uses `createAndroidSpeechEngine(context, models)`, iOS uses `createIosSpeechEngine(models)`, and JVM/Desktop uses `createJvmSpeechEngine(models, classLoader)`. All require explicit `SherpaModels`; the JVM class loader is optional. The demo preset is `SampleModels.bundledDemo()` in the sample module, not in this SDK. Factories do not download weights. Files are resolved and sessions opened when inference runs. See [configuration](../docs/model-configuration.md).

Other model families implement the provider interfaces with their own model-specific configuration. The engine owns configured providers; close it before replacement or disposal. Preserve generated sample rates, return independently owned PCM and release native allocations. Synchronous native work may finish before cancellation cleanup completes.

## Native contents

`prepareAndroidRuntime` verifies the pinned upstream AAR and extracts its wrapper JAR, four JNI libraries for each supported ABI, runtime notices and provenance into `siren/build/generated/androidRuntime`. The SDK's Android artifact carries these payloads and JNI keep rules. It carries no model assets or Compose components. Do not add a second ONNX Runtime copy without resolving duplicate native ownership.

`prepareIosSpeech` supplies pinned sherpa/ONNX static frameworks. Generated cinterop definitions embed their archive bytes into target-specific KLIBs; only required system-framework linkage remains for consumers. Generated definitions include local build search directories, but the sample host does not reference raw native frameworks. Independent artifact-consumer linkage is a separate validation gate.

`prepareJvmRuntime` verifies the official Java wrapper and six native JAR inputs. `jvmJar` embeds wrapper classes, notices and `sherpa-onnx/native/<classifier>` resources for `osx-aarch64`, `osx-x64`, `linux-aarch64`, `linux-x64`, `win-arm64` and `win-x64`. The upstream loader selects OS/architecture, extracts temporary runtime files and loads ONNX before JNI. There are no checkout-native paths or model weights in this JAR.

JVM `ModelFile.Bundled` resolves through the supplied/context class loader and materializes temporary model files owned by the engine; close removes those copies. Absolute Local files remain caller-owned. The [macOS arm64 native check](verification/jvm-native-macos-arm64-2026-11-22.json) passed; [Linux x64 inference passed in CI](verification/jvm-native-linux-x64-2026-11-22.json). The other four desktop payloads are packaged/hash-verified but execution-untested. Physical desktop microphone/speaker behavior is a sample/manual check, not SDK verification.

Targets include Android API 26+ with arm64-v8a/x86_64 JNI, arm64 iOS devices/simulators and JVM with the six packaged runtime classifiers. The pinned sample's iOS baseline is 18.5. Wasm is not supported.

## Build and local staging

From the repository root:

```bash
./gradlew :siren:jvmTest
./gradlew :siren:compileKotlinIosSimulatorArm64
./gradlew :siren:publishAllPublicationsToStagingRepository
```

Native JVM verification is opt-in and requires separately supplied compatible weights. To use the sample bundles from a source checkout:

```bash
./gradlew prepareSpeechModels
./gradlew :siren:jvmTest -Pnative=true -PmodelsDir="$(pwd)/models"
```

Alternatively point `modelsDir` at another prepared absolute model root. Default SDK tests do not load weights, and runtime-only preparation never downloads them.

The development coordinate is `io.github.debanshu777:siren:0.1.0-SNAPSHOT`, staged under `build/staging-repository`; version comes from root `gradle.properties`. This is not a public Maven release. The [relocated consumer](../validation/published-consumer-verification.json) passed four JVM tests including native macOS arm64 classpath-model inference, Android APK packaging and both iOS dynamic links from copied artifacts outside this checkout. Native libraries/metadata came from Maven coordinates, while model weights were separate test inputs. See [distribution status](../docs/library-design.md).

## Recent Changes

Added explicit JVM model inference with packaged six-platform native resources and engine-owned classpath model copies, preserving UI-free/weight-free SDK publications and local-only staging.
