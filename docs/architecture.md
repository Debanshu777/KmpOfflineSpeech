# Architecture

Siren separates model inference from a small sample application. `:siren` owns explicit public contracts and Android/iOS/JVM native providers. `:sample:composeApp` owns presentation, microphone permissions and hardware audio; Android, iOS and desktop hosts launch it. The SDK has no Compose dependency, UI/components, hardware recording/playback or model weights.

## Packages and installation identities

Source directories mirror `io.github.debanshu.siren`. SDK `audio.PcmAudio` carries mono float PCM and its actual sample rate. SDK `speech` contains provider contracts, `ModelSpeechEngine`, `SherpaModels` descriptors and platform inference factories. SDK C bindings use `io.github.debanshu.siren.internal.native` from `siren/src/iosMain/cinterop/sherpa.def`.

The sample uses `.sample` for controller/UI and `SampleModels`, `.sample.platform` for audio hosts, `.sample.android` for Android launcher/tests and `.sample.desktop` for the desktop launcher. Audio C bindings use `.sample.internal.audio` from `sample/composeApp/src/iosMain/cinterop/audio.def`. The shared sample namespace/framework bundle ID is `io.github.debanshu.siren.sample.shared`; the exported framework is `SirenApp`.

Installed identities remain Android `dev.debanshu.speech.sample` and iOS `dev.debanshu.offlinespeech`. Historical evidence retains the identifiers used in its original runs.

## Model and audio boundaries

`ModelSpeechEngine` composes independently optional `SpeechRecognizer` and `SpeechSynthesizer` providers, exposes capabilities/labels, and serializes inference and close. The short platform factory is `createSpeechEngine(context)` on Android and `createSpeechEngine()` on iOS/JVM. It defaults to `SherpaModels.bundled()`, which describes the standard app-owned Whisper tiny/Kitten nano file layout. `SherpaModels.local(root)` describes the same layout on disk. Helpers contain no weights and perform no downloads or eager loading. Custom descriptors and the original named factories remain available. See [model configuration](model-configuration.md).

`SampleModels.bundledDemo()` is a sample wrapper around the SDK's bundled-layout helper. It is not needed by SDK consumers. The sample's `SpeechPlatform` extends `SpeechEngine` with microphone permission, capture, playback and stop. Android/iOS/JavaSound audio hosts delegate inference to an injected engine. The shared `SpeechController` manages state and interaction cancellation.

```text
sample capture -> PCM + actual rate -> SDK recognition -> editable sample text
sample text -> SDK synthesis -> PCM + generated rate -> sample playback
```

Android converts captured signed PCM16 to float by dividing by 32768. iOS mixes hardware float channels to mono. Both retain the hardware rate when passing samples to sherpa, which performs the model-rate conversion. Output plays at the synthesizer's returned rate. Samples remain bounded by the sample's 20-second recording cap; built-in Whisper adapters accept 0.2–20 seconds.

## Native runtime packaging

Android `prepareAndroidRuntime` verifies the pinned upstream AAR, extracts its wrapper JAR and arm64-v8a/x86_64 JNI libraries, and preserves notices/provenance. `:siren` embeds those generated inputs with JNI keep rules. The sample depends on the SDK, not an app-local AAR. Adding another ONNX Runtime/native copy requires resolving duplicate ownership.

JVM `prepareJvmRuntime` verifies the official Java wrapper and six native resource JARs. The published JVM JAR embeds wrapper classes, notices and `sherpa-onnx/native/<classifier>` payloads for macOS/Linux/Windows arm64 and x64. Upstream loading selects the platform and extracts native resources without checkout paths. The engine resolves Bundled model resources through a supplied/context class loader into engine-owned temporary files, deleting copies on close; Local files stay caller-owned.

SDK iOS cinterop binds sherpa's C API. `GenerateBundledCinteropDefinition` copies sherpa/ONNX archive bytes and generates `staticLibraries`/`libraryPaths` definitions, so target-specific cinterop KLIBs contain native implementations. The sample has a separate audio header/Objective-C++ bridge and audio KLIB payload. Its exported framework and Xcode host retain Apple system-library linkage; the host has no raw native checkout framework/library paths.

Typed Gradle tasks execute downloads, hashing, extraction and bridge compilation. SDK runtime tasks do not depend on weights. `prepareAndroidSpeech` adds generated assets only to the sample; iOS sample linking prepares root `models/` for the host resource copy; desktop `stageDesktopModels` creates classpath `models/` resources. Generated outputs stay ignored. A null runtime model does not remove already packaged sample assets.

Desktop JavaSound reads/writes signed mono PCM16 on workers, tries 16/48/44.1 kHz input, and preserves the actual selected rate. Stop closes lines to unblock reads/writes/drain and joins workers. OS microphone permission is evaluated on opening input; the macOS package declares a purpose string. Compose Desktop and coroutines-swing live in `sample/desktopApp`, not the SDK.

## Ownership and cancellation

Each native inference call owns its session, streams/results and returned native allocations. C configurations are zero-initialized. Kotlin copies native output before destruction; pinned arrays are used only during synchronous calls. Provider work runs off Main. Engine close waits for its ownership mutex.

The common suspending `SpeechEngine.use` extension runs a block and closes in `NonCancellable` cleanup. If both the block and close fail, the original block failure is preserved with cleanup suppressed. It suits short operation scopes; lifecycle-owned engines remain open across actions and close on disposal. The `transcribe(samples, sampleRate)` extension wraps caller-owned PCM without requiring a `PcmAudio` construction at each call site. Both extensions must be imported when used outside the speech package.

The sample controller separately serializes platform operations and lifecycle cleanup. A cancelled coroutine cannot destroy resources still used by native work; results are checked for cancellation after decode/generation. The UI owns an independent scope so disposal cleanup can finish before that scope closes.

State paths include `Idle -> Starting -> Recording -> Transcribing -> Idle` and `Idle -> Synthesizing -> Playing -> Idle`. Errors return to idle with a message. `ON_STOP` cancels work and releases sample audio; disposal closes its platform and engine. Late permission callbacks cannot resume cancelled recording requests.

## Distribution and scope

The standalone [optional Gradle model helper](optional-models.md) supplies the SDK dependency and handles app-build acquisition and resource packaging by default. `bundleModels=false` keeps dependency setup and leaves model ownership to the app. Its plugin JAR contains a pinned catalog/build logic, while weights become app-owned assets/resources. The core SDK gains no weights, UI or runtime downloader. iOS direct integration must copy resources into the app; KLIB distribution alone does not supply them. The add-on 0.1.0 has independent signed Central implementation/marker publications and a `models-v0.1.0` tag.

`:siren` uses explicit API mode, API baselines, Vanniktech publication/signing and Dokka artifacts. `io.github.debanshu777:siren:0.1.0` is published to Maven Central; the [release record](releasing.md) links public delivery and Central-only consumer checks. Local staging remains under `build/staging-repository`. The [recorded relocated consumer](../validation/published-consumer-verification.json) passed four JVM tests including native macOS arm64 inference, plain Android packaging and both iOS dynamic links using copied artifacts and separate models outside the checkout. Candidates must validate their own version; historical snapshots do not establish public Central delivery. See [distribution status](library-design.md).

Android's floor is API 27; the pinned sample uses iOS 18.5 on arm64 devices/simulators. JVM supports native inference with six packaged desktop classifiers. The [macOS arm64 native check](../siren/verification/jvm-native-macos-arm64-2026-11-22.json) passed; Linux x64 inference also passed in CI; the other four payloads are packaged/hash-verified but execution-untested. Desktop physical microphone/speaker behavior remains manually unverified. Wasm, background audio, streaming and automatic route recovery are not claimed. All UI and hardware concerns remain sample-only.

## Recent Changes

Documented independent publication of the build-time model add-on; runtime contracts and application-owned UI/audio are unchanged.
