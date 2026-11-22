# Siren SDK boundary and distribution status

Siren is the UI-free SDK module `:siren`, with Kotlin root package `io.github.debanshu.siren` and explicit API mode. **Local staging is configured** for `io.github.debanshu777:siren:0.1.0-SNAPSHOT` under `build/staging-repository`. No public Maven release has been made.

## Implemented separation

| Area | Implemented structure | Remaining release gate |
| --- | --- | --- |
| Contracts | SDK `audio.PcmAudio`, recognizer, synthesizer and engine contracts | Target-specific compatibility policy and API/ABI checks |
| Providers | SDK Android, iOS and JVM inference with explicit typed `SherpaModels` | Rerun native artifact verification after provider changes |
| Android runtime | Pinned wrapper JAR, two ABI JNI payloads, notices and consumer rules embedded in SDK | Inspect staged contents and test duplicate-runtime ownership |
| JVM runtime | Java wrapper and six native resource sets embedded in JVM JAR | Five packaged targets remain execution-untested |
| iOS runtime | sherpa/ONNX static archives embedded in generated cinterop KLIBs | Physical-device inference/audio checks |
| Sample | Controller, Compose screen, hardware audio, permissions and `SampleModels.bundledDemo()` | Repeat app/hardware checks for current artifacts |
| Distribution | Local Maven root/target publications and native cinterop artifacts | Maintain relocated consumer evidence for every changed artifact |

```text
siren/                 UI-free inference SDK
sample/composeApp/     controller, Compose, permissions and audio hosts
sample/androidApp/     Android launcher and tests
sample/iosApp/         SwiftUI host
sample/desktopApp/     Compose Desktop launcher and classpath sample models
```

The SDK contains **zero UI or components** and has no Compose dependency. It accepts caller-owned mono PCM and returns text or independently owned generated PCM at the correct rate. Hardware audio, application lifecycle, storage and weight acquisition remain host concerns. No camera, vision, generic AI framework or placeholder extension structure is included.

## Optional providers and models

`SpeechRecognizer` and `SpeechSynthesizer` are independently replaceable. `ModelSpeechEngine` serializes provider operations and close, exposes capabilities and labels, and rejects calls after closure:

```kotlin
fun synthesisOnly(provider: SpeechSynthesizer): SpeechEngine =
    ModelSpeechEngine(synthesizer = provider)
```

`SherpaModels` configures compatible Whisper/Kitten exports with bundled or local file descriptors. Native factories require that explicit descriptor; the SDK has no demonstration preset. Other model families implement the SPI and their own concrete configuration. Close an engine before replacing its providers.

SDK artifacts contain no model weights. The default sample preparation obtains and packages both demonstration bundles. Setting a model to null disables a runtime capability; it does **not** remove JNI libraries or already packaged sample assets. Artifact size/build-time selection is a separate concern.

## Native distribution implementation

Android `prepareAndroidRuntime` is independent of weight preparation. It verifies the upstream AAR, extracts the wrapper JAR, checks the expected native library set for arm64-v8a/x86_64, and preserves notices/provenance. The SDK packages these generated inputs instead of exposing an app-local AAR dependency. Inspect both the SDK AAR and the final consumer APK to confirm payload and duplicate-runtime behavior.

`prepareJvmRuntime` embeds official wrapper classes, notices and six native resource sets in the JVM JAR. Upstream loading selects OS/architecture without checkout paths. JVM Bundled weights resolve through the class loader into engine-owned temporary copies; Local files remain caller-owned. The sample supplies JavaSound audio separately.

For iOS, generated definitions copy static archives and set `staticLibraries`/`libraryPaths`, embedding their bytes in target-specific cinterop KLIBs. SDK runtime archives and the sample's separate AVFoundation archive have distinct bindings. The sample host links `SirenApp.framework` and Apple system libraries, without raw native checkout paths. Kotlin documents the experimental [static-library inclusion mechanism](https://kotlinlang.org/docs/native-definition-file.html). In-repository linkage alone does not prove portable consumption.

## ShaderX reference and validation stages

The reviewed [ShaderX library build](https://github.com/Debanshu777/ShaderX/blob/main/shaderx/build.gradle.kts) demonstrates a dedicated module, explicit API, compatibility checks, documentation and publication metadata. Its [KMP consumer](https://github.com/Debanshu777/ShaderX/blob/main/validation/kmp-consumer/build.gradle.kts) switches between project and versioned artifacts, while [frozen fixtures](https://github.com/Debanshu777/ShaderX/blob/main/validation/binary-compatibility/README.md) preserve old callers. Siren borrows this separation, not ShaderX's Compose API or target support.

Siren currently uses Gradle `maven-publish` for local staging. Vanniktech's [KMP publication support](https://vanniktech.github.io/gradle-maven-publish-plugin/what/) is a possible release-tooling reference, not an installed requirement or evidence of publication.

1. **Build and stage locally.** SDK/sample extraction and staging publications build. Version comes from root `gradle.properties`; `:siren:publishAllPublicationsToStagingRepository` writes only to the local repository.
2. **Validate independent consumers.** The [relocated artifact checks](../validation/published-consumer-verification.json) passed four JVM tests including macOS arm64 native classpath-model inference, a plain Android APK with eight JNI entries and no weights, and both arm64 dynamic iOS links. Fixture, repository and separate test models were copied outside the checkout, with no substitution/native checkout paths. These checks do not establish hardware audio or public repository delivery.
3. **Establish compatibility.** Add target-specific API/ABI checks and preserved public consumer contracts; document model-format and toolchain support. Earlier sample/hardware evidence must not substitute for checks of extracted artifacts.
4. **Decide a public release.** Complete release documentation, native redistribution review and signing/publication configuration. Local staging does not authorize a public release.

The baseline is Android API 26 and arm64 iOS targets, with iOS 18.5 for the pinned sample. JVM packages macOS/Linux/Windows arm64/x64 natives; only [macOS arm64 inference](../siren/verification/jvm-native-macos-arm64-2026-11-22.json) is execution-verified. Other five payloads and desktop audio hardware remain untested. Wasm is not supported.

## Recent Changes

Added native JVM inference and a separate JavaSound desktop sample; weights/UI remain sample-only and public release remains separate from staging.
