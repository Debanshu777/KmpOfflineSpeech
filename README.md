# KMP Offline Speech

A small Android + iOS example of **speech-to-text with Whisper** and **text-to-speech with Kitten**, using Kotlin Multiplatform, Compose Multiplatform, and [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).

Start with the [step-by-step guide](docs/medium-draft.md) to add speech models to an existing KMP project. This repository provides the completed reference implementation.

Record a short English phrase, transcribe it, edit the text, and hear a generated voice. Models run locally on CPU. This project has no CaraML dependency, cloud inference service, database, dependency injection framework, or model catalog.

<img src="docs/images/speech-demo.gif" width="320" alt="Actual Android app recording speech, transcribing it and playing it back">

*Actual Android recording → transcription → playback. See [capture and verification notes](docs/demo.md) for details.*

- [End-to-end tutorial / Medium source](docs/medium-draft.md)
- [Architecture and limits](docs/architecture.md)
- [Actual app capture](docs/demo.md)
- [Verification and development](docs/development.md)
- [iOS integration notes](docs/ios-notes.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Run Android

Install JDK 21+, Android SDK platform 37.0 and build tools 37.0.0 through Android Studio. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir=/your/Android/sdk`.

```bash
./gradlew :androidApp:assembleDebug
```

Open the project in Android Studio and run `androidApp`, or install and launch the APK with the [Android CLI](https://developer.android.com/tools/android-cli):

```bash
android run --apks=androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --activity=dev.debanshu.speech.android.MainActivity
```

Gradle prepares the pinned runtime and model assets before compilation. Grant microphone permission when tapping **Record**. Tap **Stop & transcribe**, then **Speak**. For an initial TTS check, simply speak the prefilled text. The app has no Internet permission: downloads happen only in the development build.

## Run iOS

On an Apple Silicon Mac with full Xcode selected (`xcode-select -p`):

```bash
./gradlew prepareIosSpeech
open iosApp/iosApp.xcodeproj
```

Select the **OfflineSpeech** scheme and an iOS 18.5+ simulator, then Run. The host's Gradle build phase prepares the selected audio bridge, builds the shared Kotlin framework and links it into the app. For a physical iPhone, choose your signing team in Xcode. Intel simulator builds are outside this sample's targets.

The iOS runtime ZIPs are pinned to the exact artifacts in sherpa's tagged Swift package manifest; the ONNX Runtime package tag and binary filename differ. See [the dependency lock](scripts/dependencies.json) for the URLs, hashes and provenance.

## Project layout

```text
composeApp/src/commonMain/   speech contract, state machine, Compose screen
composeApp/src/androidMain/  Kotlin sherpa API, AudioRecord, AudioTrack
composeApp/src/iosMain/      direct C API interop + a small AVFoundation bridge
androidApp/                 Android launcher
iosApp/                     SwiftUI host and Xcode project
buildSrc/                   typed Gradle preparation tasks and regression tests
scripts/                    pinned artifact manifest + optional native smoke source
```

The generated `models/`, runtime binaries and model assets are ignored by Git. Typed Gradle tasks handle downloads, checksum verification, extraction and native audio compilation. Initial downloads require Internet and several hundred MB of disk space. Gradle declares the inputs and outputs; cached downloads are verified before reuse. Model files are bundled for this teaching sample, so the app package is intentionally large.

## Deliberate limits

- Foreground operation, one task at a time; recording stops at 20 seconds.
- English Whisper configuration and English Kitten voice, speaker ID 0.
- TTS input is limited to 500 characters; no streaming recognition or synthesis.
- Sessions are created per inference call and released afterward, favoring understandable ownership over startup speed.
- Cancel suppresses results and waits for synchronous native inference to return before cleanup. It does not forcibly interrupt the native decoder.
- Android targets arm64-v8a and x86_64; iOS targets arm64 devices and Apple Silicon simulators. No desktop app is included; the JVM target runs shared tests only.
- Audio interruption/route recovery, audio focus, background services, VAD and production download/update flows are future work.

## Development

```bash
./gradlew checkSpeechSetup
./gradlew :composeApp:jvmTest
```

See [development notes](docs/development.md) for native checks, the manual verification recipe, and recorded verification status. All versions used by this sample are explicit in the Gradle files and `scripts/dependencies.json`.

## Recent Changes

Tightened the model integration tutorial and added a minimal Compose screen showing how record, transcript editing and playback connect to the shared controller. The README now previews the actual recording → transcription → playback GIF.

## License

Sample source: [MIT](LICENSE). Downloaded runtimes, models and dictionaries retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
