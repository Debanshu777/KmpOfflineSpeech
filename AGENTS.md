# Standalone speech example

This project teaches local speech recognition and synthesis on Android and iOS. Keep it independent of CaraML and avoid adding model hubs, storage frameworks, dependency injection or chat infrastructure.

- `composeApp/commonMain`: shared contract, controller, Compose UI.
- `composeApp/androidMain`: official sherpa Kotlin wrapper + Android audio.
- `composeApp/iosMain`: direct C API + small AVFoundation bridge.
- `androidApp` and `iosApp`: thin platform hosts.
- `scripts/dependencies.json`: pinned runtime/model URLs and hashes. Generated binaries and weights remain ignored.
- `buildSrc`: typed Gradle preparation tasks, pinned downloads, safe extraction and regression tests. Keep this workflow entirely in Gradle; no Python setup scripts.

Run `./gradlew checkSpeechSetup :composeApp:jvmTest :androidApp:assembleDebug` for relevant changes. Android builds prepare the AAR/model assets automatically. Native compatibility: `./gradlew :androidApp:connectedDebugAndroidTest` on a connected device. For iOS, build the checked-in OfflineSpeech Xcode scheme; Gradle prepares dependencies and the target audio bridge automatically. Explicit setup tasks are `prepareSpeechModels`, `prepareAndroidSpeech`, `prepareIosSpeech`, `prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64`. Use `--offline` only after all required Gradle dependencies and speech artifacts are cached. Follow `docs/development.md` for exact commands and platform limits.

Keep root/module READMEs and the tutorial reconciled with code changes, and update concise Recent Changes entries. Report checks actually run. Do not claim microphone/speaker verification or performance from compilation or a synthetic model roundtrip. Every native allocation needs clear ownership and paired destruction; cancellation must wait for synchronous native work before teardown.
