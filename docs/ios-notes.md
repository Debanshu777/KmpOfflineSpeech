# iOS integration notes

The shared Kotlin UI calls `IosSpeechPlatform`. Recognition and synthesis call the
pinned sherpa-onnx C API through Kotlin/Native cinterop. The small Objective-C++
file only captures and plays audio using AVFoundation; it contains no model logic.

`scripts/prepare_ios.py` compiles that audio file for device arm64 and simulator
arm64. It requires full Xcode. Download the iOS dependencies with `scripts/setup.py`
before running the bridge preparation or Gradle. Gradle compiles the Kotlin
framework against the selected headers. The Xcode host links the static sherpa,
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

## Recent Changes

- Added a direct C API iOS adapter, bounded AVFoundation capture and playback, and
  a runnable SwiftUI host independent of CaraML.
