# iOS integration notes

The shared Kotlin UI calls `IosSpeechPlatform`. Recognition and synthesis call the
pinned sherpa-onnx C API through Kotlin/Native cinterop. The small Objective-C++
file only captures and plays audio using AVFoundation; it contains no model logic.

The Gradle tasks `prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64`
compile that audio file using full Xcode. They depend on `prepareIosSpeech`, which
prepares the pinned frameworks and models. Native cinterop and linking invoke the
matching task automatically, so opening and building the Xcode host prepares its
own inputs. For explicit setup, run `./gradlew prepareIosSpeech`. Gradle compiles
the Kotlin framework against the selected headers. The Xcode host links the static sherpa,
ONNX Runtime and audio bridge libraries into the final application.

Open `iosApp/iosApp.xcodeproj`, select the OfflineSpeech scheme and an iOS 18.5+
arm64 simulator on an Apple Silicon Mac, and run. A physical device also needs your development signing team.
This sample declares `iosArm64` and `iosSimulatorArm64`; an Intel simulator target
is not included.
The host minimum is iOS 18.5 because the pinned Compose toolchain includes an ICU
object built for that baseline. This is a sample compatibility choice, not a
general minimum for sherpa-onnx or Kotlin Multiplatform.
The checked-in host copies the `models` folder as a folder resource and builds
the Kotlin framework in an Xcode build phase. Keep User Script Sandboxing disabled
for that Gradle phase. No Xcode project generator is required.

The microphone's native rate may be 44.1 or 48 kHz. Capture averages input channels
to mono float samples and keeps the real sample rate. `SherpaOnnxAcceptWaveformOffline`
resamples those samples for Whisper; labelling them as 16 kHz would distort the
recording. The native buffer is capped at 20 seconds. Kitten's returned sample rate
is used for playback instead of reusing the microphone rate.

Inference runs on `Dispatchers.Default` under one mutex. Each call owns its native
session and frees its stream/results/audio in `finally`. Cancelling a coroutine
cannot interrupt a synchronous decode already in progress, but its result is
discarded. `close()` waits for native work before releasing audio handles. This
sample is intended for short foreground clips; it has no background recording or
route/interruption recovery. Stop and retry after an interruption.

## Native runtime smoke test

The recorded [iOS native result](ios-native-smoke.json) generated a short KittenTTS
phrase at 24 kHz and passed the returned PCM and rate to Whisper, which recognized
the phrase. This verifies the pinned C API runtime and model pair on an iOS
simulator. It does not exercise the Kotlin adapter, microphone capture or speaker
output. The [UI capture](demo.md) separately verifies the Compose app launches.

To reproduce from the repository root after setup, with an arm64 simulator already
booted (and only one simulator booted when using `booted`):

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

The executable prints JSON and returns a nonzero exit status unless the transcript
contains `quick`, `brown` and `fox`. Its optional argument is the model directory;
without it the executable uses `models` relative to its working directory.

## Recent Changes

- Added a direct C API iOS adapter, bounded AVFoundation capture and playback, and
  a runnable SwiftUI host independent of CaraML, with Gradle preparing native
  inputs automatically and a reproducible simulator TTS-to-STT smoke test.
