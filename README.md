# Siren

A **UI-free Kotlin Multiplatform speech SDK** for on-device recognition and synthesis, with separate Android, iOS and desktop sample hosts. The built-in providers run compatible Whisper and Kitten exports through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).

`:siren` exposes PCM values, replaceable recognition/synthesis providers and explicit model configuration. It has no Compose dependency, UI components, microphone permission dialogs, hardware audio implementation or bundled model weights. [`sample/`](sample/README.md) supplies those application concerns and the demonstration models.

Recognition and synthesis can be configured independently. Use bundled or local files, omit either capability, or implement another concrete model adapter. Start with [model configuration](docs/model-configuration.md) and the [SDK README](siren/README.md). The [tutorial](docs/medium-draft.md) explains the underlying model integration.

The local staging coordinate is **`io.github.debanshu777:siren:0.1.0-SNAPSHOT`**, written to `build/staging-repository`. This is a development artifact, **not a public Maven release**. See [distribution status](docs/library-design.md) for remaining consumer and compatibility gates.

<img src="docs/images/siren-demo.gif" width="320" alt="Actual Android sample recording speech, transcribing it and playing it back">

*The sample records, transcribes, edits and reads aloud. Capture provenance and verification limits are in [demo notes](docs/demo.md).*

## Run the Android sample

Install JDK 21+, Android SDK platform 37.0 and build tools 37.0.0. Set `ANDROID_HOME` or an ignored `local.properties` with your SDK directory.

```bash
./gradlew :sample:androidApp:assembleDebug
```

Run `sample/androidApp` from Android Studio, or use the [Android CLI](https://developer.android.com/tools/android-cli):

```bash
android run --apks=sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --activity=io.github.debanshu.siren.sample.android.MainActivity
```

Assembly prepares the SDK runtime and sample-only model assets. Grant microphone access when recording; stop and transcribe, then Speak. The explicit `SampleModels.bundledDemo()` preset selects Whisper tiny and Kitten nano. The sample has no Internet permission: downloads occur during build preparation.

## Run the iOS sample

On an Apple Silicon Mac with full Xcode selected:

```bash
open sample/iosApp/iosApp.xcodeproj
```

Select the **Siren** scheme and an iOS 18.5+ arm64 simulator, then Run. The host's Gradle phase prepares dependencies, builds `SirenApp.framework` and links it. Physical devices need your signing team. Intel simulators are not targeted.

SDK cinterop KLIBs contain the sherpa/ONNX Runtime static archives. The sample separately embeds its AVFoundation audio archive. The host links the resulting Kotlin framework and Apple system libraries; it has no raw sherpa/ONNX checkout search paths. See [iOS notes](docs/ios-notes.md).

## Run the desktop sample

```bash
./gradlew :sample:desktopApp:run
./gradlew :sample:desktopApp:createDistributable
```

The JVM host uses the shared screen with a sample-only JavaSound audio adapter. `stageDesktopModels` prepares demonstration weights as classpath resources. The SDK's `createJvmSpeechEngine(models)` loads packaged runtime resources; neither runtime preparation nor the SDK JAR contains weights.

`createDistributable` creates an app image for the current host OS and requires a full, jpackage-capable JDK distribution accepted by Compose's packaging checks; see the [desktop host guide](sample/desktopApp/README.md) for `JAVA_HOME`. Installer creation is OS-specific. JavaSound asks the OS for microphone access when opening input; development JDK and packaged-app permission identities can differ. The macOS app includes its purpose string. No permission or volume setting changes automatically.

macOS arm64 application-image creation passed using a full JBR 21 toolchain. Its packaging version is `1.0.0` to satisfy macOS jpackage rules; the SDK remains `0.1.0-SNAPSHOT`. This packaging check does not establish physical audio behavior.

```bash
./gradlew :sample:desktopApp:nativeSpeechSmoke
```

This checks classpath models/runtime without opening audio hardware. The [macOS arm64 SDK native check](siren/verification/jvm-native-macos-arm64-2026-11-22.json) passed; [Linux x64 inference also passed in CI](siren/verification/jvm-native-linux-x64-2026-11-22.json). The other four desktop payloads are packaged and hash-verified, with execution untested. Desktop microphone/speaker behavior remains a separate manual check.

## SDK builds and local staging

```bash
./gradlew :siren:jvmTest :sample:composeApp:jvmTest checkSpeechSetup
./gradlew :siren:publishAllPublicationsToStagingRepository
```

The second command writes only to the repository-local staging Maven directory. Android SDK preparation embeds the pinned wrapper JAR, JNI libraries for arm64-v8a/x86_64 and notices. JVM preparation embeds the Java wrapper plus six OS/architecture native resource sets in the portable JVM JAR. Neither task prepares model weights. Native SDK preparation embeds target-specific static archives in cinterop KLIBs. Initial preparation needs network access; use `--offline` after the selected Gradle dependencies and speech artifacts have been cached.

The [recorded relocated consumer](validation/published-consumer-verification.json) passed four JVM tests including native macOS arm64 inference with classpath models, plain Android APK packaging and both iOS dynamic links. The fixture, Maven repository and test models were copied outside the SDK checkout; no project/native checkout paths supplied the implementation. See [distribution status](docs/library-design.md) and [verification](docs/development.md). No public publication is needed for local staging.

## Project layout

```text
siren/                 UI-free SDK, public contracts and platform inference
sample/composeApp/     controller, Compose screen, permissions and hardware audio
sample/androidApp/     Android launcher and instrumentation tests
sample/iosApp/         SwiftUI host and Xcode project
sample/desktopApp/     Compose Desktop launcher and sample model resources
buildSrc/              verified artifact preparation and cinterop bundling tasks
scripts/               pinned artifact manifest and optional native smoke source
models/                generated sample-only weights; ignored by Git
```

First-party Kotlin packages use `io.github.debanshu.siren`. The Android application ID remains `dev.debanshu.speech.sample` and the iOS host bundle ID remains `dev.debanshu.offlinespeech`; see [architecture](docs/architecture.md).

## Scope and limits

- Android API 26+, arm64-v8a and x86_64; arm64 iOS device/simulator targets. The pinned sample uses iOS 18.5+.
- JVM packages native resources for macOS arm64/x64, Linux arm64/x64 and Windows arm64/x64. Native inference is verified on macOS arm64 and Linux x64 (CI); the other four payloads remain untested. Wasm is not supported.
- Built-in Whisper adapters accept 0.2–20-second clips; the sample records at most 20 seconds and limits TTS input to 500 characters.
- The sample's default models use English recognition and an English voice, speaker zero. Explicit configurations can select compatible files and supported settings.
- Cancellation suppresses obsolete results and waits for synchronous native inference before teardown; it does not forcibly interrupt decoding.
- The sample is foreground-only. Background services, streaming, VAD, audio-focus/route recovery and production model installation are outside its scope.
- Runtime omission does not remove native binaries or already packaged sample weights. The SDK carries no model weights; the default sample packages both bundles.

## Documentation

- [SDK](siren/README.md) and [sample](sample/README.md)
- [Model configuration](docs/model-configuration.md)
- [Architecture](docs/architecture.md) and [distribution gates](docs/library-design.md)
- [Tutorial / Medium source](docs/medium-draft.md)
- [Development and measured evidence](docs/development.md)
- [Capture notes](docs/demo.md) and [third-party notices](THIRD_PARTY_NOTICES.md)

## Recent Changes

Added a manual [signing-secret CI check](validation/signing-check/README.md) that signs a harmless probe with Gradle and verifies it against the published public key. It runs no SDK publication, release or tag commands. Maven credential presence is checked; authentication is not tested.

## License

First-party source: [MIT](LICENSE). Runtime binaries, models and dictionaries retain their own licenses and notices.
