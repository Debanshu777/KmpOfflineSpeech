# iOS integration notes

The SDK `createSpeechEngine()` composes optional Whisper/Kitten providers through the pinned sherpa C API using the standard `SherpaModels.bundled()` layout. The app supplies the weights in its application resources. Explicit model descriptors and the original `createIosSpeechEngine(models)` remain available. The sample passes its `SampleModels.bundledDemo()` wrapper to the factory, then injects the engine into `sample.platform.IosSpeechPlatform` for AVFoundation audio and permission handling.

## SDK and audio native payloads

`siren/src/iosMain/cinterop/sherpa.def` declares bindings in `io.github.debanshu.siren.internal.native`. For each target, `:siren:bundleSherpaCinteropIosArm64` or `:siren:bundleSherpaCinteropIosSimulatorArm64` prepares a generated definition and copies the sherpa/ONNX static archives beside it. The generated `staticLibraries`/`libraryPaths` entries embed those archive bytes into the cinterop KLIB.

The sample's `sample/composeApp/src/iosMain/cinterop/audio.def` separately generates `.sample.internal.audio` bindings. `prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64` compile the small Objective-C++ AVFoundation bridge with full Xcode. Sample `bundleAudioCinterop...` tasks embed that archive in its audio KLIB. The bridge contains no model inference.

Cinterop processing and framework linking declare their preparation dependencies. `prepareIosSpeech` prepares runtime frameworks only; it does not download weights. Sample framework linking additionally prepares `models/`. Runtime-only SDK compilation can run independently:

```bash
./gradlew :siren:compileKotlinIosSimulatorArm64
```

The sample exports static `SirenApp.framework`. Its Xcode host searches for that generated framework and links Apple system libraries; it has no raw sherpa/ONNX `-F` paths or local native archive search paths. The [recorded relocated consumer](../validation/published-consumer-verification.json) dynamically linked both arm64 iOS targets from copied Maven artifacts without checkout-native paths. This verifies dependency closure for that snapshot, not physical audio or arbitrary toolchains. See [distribution status](library-design.md).

## Run the sample

```bash
open sample/iosApp/iosApp.xcodeproj
```

Choose the **Siren** scheme and an iOS 18.5+ arm64 simulator on an Apple Silicon Mac. Physical devices need your signing team. The build declares `iosArm64` and `iosSimulatorArm64`, not Intel simulator support. The 18.5 host minimum reflects an ICU object in the pinned Compose toolchain, not a universal sherpa/KMP minimum.

The Xcode phase changes to the repository root and invokes `:sample:composeApp:embedAndSignAppleFrameworkForXcode`. Keep User Script Sandboxing disabled for that phase. Swift imports `SirenApp` and calls `MainViewControllerKt.MainViewController()`. The host bundle ID remains `dev.debanshu.offlinespeech`.

The host copies root `models` as a folder resource, retaining paths such as `models/whisper/tiny-tokens.txt`. It declares `NSMicrophoneUsageDescription` and `CADisableMinimumFrameDurationOnPhone`. SDK users supplying `ModelFile.Local` own their file installation and need not package the sample models.

## Audio and lifetime

AVAudioEngine captures the input's actual rate, commonly 44.1 or 48 kHz, averages channels to mono and caps storage at 20 seconds. The SDK passes that rate to `SherpaOnnxAcceptWaveformOffline`, which resamples for Whisper. Merely labelling samples as 16 kHz would change their meaning. Playback uses the synthesizer's returned rate.

SDK inference runs on Default under engine ownership. Each operation creates and frees its native session, streams/results and generated buffers in `finally`. Cancellation suppresses obsolete results after synchronous native work returns; teardown must wait. Sample audio handles stay on Main, and its controller closes the engine on disposal. Background recording and automatic route/interruption recovery are not implemented.

## Native runtime smoke test

The historical [C API result](ios-native-smoke.json) checks a synthetic Kitten-to-Whisper roundtrip on a simulator. It does not validate an iPhone microphone/speaker or the extracted Kotlin SDK. The diagnostic intentionally links downloaded frameworks directly; this is separate from the sample/SDK KLIB distribution path.

After `./gradlew prepareIosSpeech prepareSpeechModels`, with an arm64 simulator booted:

```sh
mkdir -p build
xcrun --sdk iphonesimulator clang++ -std=c++17 -O2 \
  -target arm64-apple-ios18.5-simulator \
  -isysroot "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
  -I libs/ios/SherpaOnnxC.xcframework/ios-arm64_x86_64-simulator/SherpaOnnxC.framework/Headers \
  -F libs/ios/SherpaOnnxC.xcframework/ios-arm64_x86_64-simulator \
  -F libs/ios/onnxruntime.xcframework/ios-arm64_x86_64-simulator \
  -framework SherpaOnnxC -framework onnxruntime -framework Foundation \
  -framework CoreML -framework CoreFoundation -framework Accelerate \
  scripts/ios_native_smoke.cpp -o build/ios-native-smoke
xcrun simctl spawn booted "$(pwd)/build/ios-native-smoke" "$(pwd)/models"
```

Use only one booted simulator when targeting `booted`. The executable prints JSON and requires `quick`, `brown` and `fox` in the transcript. See [development](development.md) for current build/consumer evidence and [demo notes](demo.md) for capture provenance.

## Recent Changes

Documented the default iOS factory and app-owned bundled model files. Both dynamic consumer links remain covered by relocated-artifact validation.
