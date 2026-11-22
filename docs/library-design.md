# Siren SDK boundary and release status

Siren is the UI-free SDK module `:siren`, using Kotlin package `io.github.debanshu.siren` and Maven coordinates `io.github.debanshu777:siren`. Candidate **`0.1.0` is prepared locally and not published**. Publication is deferred; local staging must not be described as Central availability.

## SDK and application boundary

| Area | Implemented design | Verification/release responsibility |
| --- | --- | --- |
| Contracts | Explicit public PCM, recognition, synthesis and engine APIs | Versioned API baselines and consumer tests |
| Providers | Android, iOS and JVM inference with explicit `SherpaModels` | Compatible model exports, native lifetime and target checks |
| Android runtime | Embedded wrapper/JNI, two ABIs, consumer rules and notices | Final AAR/APK inspection and native ownership |
| JVM runtime | Java wrapper plus six native resource sets in JVM JAR | macOS arm64/Linux x64 executed; four targets untested |
| iOS runtime | Static sherpa/ONNX archives inside cinterop KLIBs | Both arm64 dynamic links and complete native dependency closure |
| Sample | UI, hardware audio, permissions and demo preset/weights | App-specific lifecycle, privacy and hardware behavior |
| Publication | Vanniktech, signing, POM metadata, sources and Dokka artifacts | Release checks, native redistribution readiness and Central verification |

```text
siren/                 UI-free inference SDK
sample/composeApp/     controller, Compose, permissions and audio hosts
sample/androidApp/     Android launcher and tests
sample/iosApp/         SwiftUI host
sample/desktopApp/     desktop launcher and classpath sample models
```

The SDK has no UI/components, Compose dependency, microphone handling, playback or model weights. It accepts caller-owned mono PCM and returns text or independent PCM with its actual sample rate. No camera/vision structure or generic AI framework is included.

## Model selection and ownership

`SpeechRecognizer` and `SpeechSynthesizer` are independently replaceable. `ModelSpeechEngine` serializes provider calls and close, rejects calls after closure, and exposes optional capabilities/labels:

```kotlin
fun synthesisOnly(provider: SpeechSynthesizer): SpeechEngine =
    ModelSpeechEngine(synthesizer = provider)
```

Built-in factories require `SherpaModels`. Its Whisper/Kitten descriptors identify compatible bundled or local files; they do not download models. Other model families supply concrete adapters. The `SampleModels.bundledDemo()` preset remains sample-only.

Default sample builds prepare both demonstration bundles. Setting a provider to null disables a runtime capability but does not remove already packaged assets or native binaries. Close the engine before replacing providers. JVM bundled resources are copied into engine-owned temporary storage; Local files remain caller-owned.

## Native distribution

`prepareAndroidRuntime` verifies the upstream AAR, checks arm64-v8a/x86_64 native contents and preserves wrapper classes/notices in the SDK artifact. `prepareJvmRuntime` embeds official Java classes, six native resource sets and notices; upstream loading selects OS/architecture without checkout-native paths.

For iOS, generated `staticLibraries`/`libraryPaths` definitions include native archive bytes inside target-specific KLIBs. SDK model runtimes and sample AVFoundation audio have separate bindings. The sample host links its Kotlin framework and Apple system libraries, without raw sherpa/ONNX checkout paths. Kotlin documents this experimental [static-library inclusion mechanism](https://kotlinlang.org/docs/native-definition-file.html).

Bundled native binaries retain upstream licensing obligations independently of the SDK's MIT source license. Exact artifact provenance and redistribution notices are release inputs, not properties established by successful compilation. Current audit status belongs with the release record and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Release engineering and evidence

[ShaderX's library build](https://github.com/Debanshu777/ShaderX/blob/main/shaderx/build.gradle.kts) informed the dedicated module, explicit API, documentation, publication metadata and independent consumer approach. Siren does not inherit ShaderX's Compose API or platform claims.

The build now uses [Vanniktech's KMP publication support](https://vanniktech.github.io/gradle-maven-publish-plugin/what/), Central signing and Dokka. `sirenVersion` in root `gradle.properties` is the version source. Unsigned local staging remains available without publication credentials. `checkKotlinAbi` compares baselines under `siren/api`; updates require intentional public-API review.

The [recorded relocated consumer](../validation/published-consumer-verification.json) passed four JVM tests including native macOS arm64 inference, a plain Android APK with eight JNI entries/no weights, and both arm64 dynamic iOS links. Fixture, Maven repository and separate test models were copied outside the checkout. Release candidates must rerun artifact checks against their own version, not substitute historical snapshot evidence.

The [future release path](releasing.md) uses a main-only manual dispatch with configured credentials, then validates source/API, redistribution readiness and relocated artifacts before publication and public Central verification. Remote steps are deferred. Exact ONNX Runtime source/notice records were recovered; remaining Sherpa/transitive/compiler inventory and source coverage still require review. The [current audit](../THIRD_PARTY_NOTICES.md) is authoritative; `verifyNativeRedistribution` remains fail-closed.

Android starts at API 26; the pinned sample uses iOS 18.5 arm64 targets. JVM inference is verified on [macOS arm64](../siren/verification/jvm-native-macos-arm64-2026-11-22.json) and [Linux x64 CI](../siren/verification/jvm-native-linux-x64-2026-11-22.json). Other desktop targets and desktop hardware audio remain unverified. Wasm is unsupported.

## Recent Changes

Prepared local `0.1.0` release configuration and API baselines; publication remains deferred with explicit native-notice/provenance readiness requirements.
