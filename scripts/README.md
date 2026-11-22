# Artifact manifest and native verification

`dependencies.json` pins runtime/model URLs, expected sizes, SHA-256 hashes and runtime notices/provenance. Typed [Gradle build tools](../buildSrc/README.md) read it during task execution. This directory does not provide an independent setup script.

Explicit root preparation tasks:

```bash
./gradlew prepareAndroidRuntime
./gradlew prepareJvmRuntime
./gradlew prepareIosSpeech
./gradlew prepareSpeechModels
./gradlew prepareAndroidSpeech
```

The first three prepare SDK runtime inputs only. JVM preparation pins the official Java wrapper and six OS/architecture native resources; Android and iOS have their own runtime artifacts. `prepareSpeechModels` obtains default sample weights/notices; `prepareAndroidSpeech` publishes them as Android sample assets, and desktop `stageDesktopModels` publishes classpath sample resources. Normal builds declare these dependencies automatically. Generated binaries, weights and `.cache/downloads` remain ignored.

`prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64` compile the sample AVFoundation bridge using full Xcode. Generated cinterop tasks embed SDK runtime archives and sample audio archives in separate KLIBs. The sample host has no raw native runtime checkout path dependency.

`ios_native_smoke.cpp` is an optional direct C API diagnostic on an already booted arm64 iOS simulator: Kitten generates PCM and Whisper transcribes it. See [iOS notes](../docs/ios-notes.md#native-runtime-smoke-test) for exact commands and [historical output](../docs/ios-native-smoke.json). This diagnostic does not exercise the Kotlin SDK API, microphone or speaker hardware.

Run `./gradlew checkSpeechSetup` for checksum/cache/safe-extraction regressions. SDK JVM native inference can be checked with `:siren:jvmTest -Pnative=true -PmodelsDir=/absolute/model/root`; desktop `:sample:desktopApp:nativeSpeechSmoke` uses the actual classpath sample models without audio devices. See [development](../docs/development.md) and [distribution status](../docs/library-design.md) for evidence. Model weights are never part of the SDK publication; macOS arm64 and Linux x64 (CI) native inference are verified; the other four desktop payloads remain execution-untested.

## Recent Changes

Pinned the official JVM Java/native artifacts alongside Android/iOS, with all SDK runtime preparation independent of sample weight packaging.
