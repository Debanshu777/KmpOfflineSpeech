# Artifact manifest and native verification

`dependencies.json` pins runtime/model URLs, sizes and SHA-256 hashes. The typed
[Gradle build tools](../buildSrc/README.md) read this manifest during task execution.
Use `./gradlew prepareAndroidSpeech` or `./gradlew prepareIosSpeech` for explicit
preparation; normal platform builds prepare their own inputs automatically.
Use `./gradlew prepareSpeechModels` to prepare only the model files and notices.
Generated files and `.cache/downloads/` are ignored by Git.

The Gradle tasks `prepareIosAudioIosArm64` and
`prepareIosAudioIosSimulatorArm64` compile the small audio bridge with full Xcode.
Gradle tracks source/header and native toolchain inputs and invokes the matching
task before cinterop and framework linking. There are no Python setup steps.

`ios_native_smoke.cpp` optionally checks the native C API model pair on an already
booted arm64 iOS simulator: Kitten generates a phrase and Whisper transcribes its
PCM. See [iOS notes](../docs/ios-notes.md#native-runtime-smoke-test) for the exact
compile/run commands and [the recorded result](../docs/ios-native-smoke.json).
It does not test the Kotlin UI adapter or microphone/speaker hardware.

Run `./gradlew checkSpeechSetup` from the root for checksum, cache and safe archive
extraction regressions. See [development](../docs/development.md) for the full
verification recipe.

## Recent Changes

Moved runtime/model preparation and native audio compilation into Gradle, retained
the pin manifest and optional native simulator verification, and removed Python.
