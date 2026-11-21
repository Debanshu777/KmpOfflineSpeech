# Offline Speech Recognition and Text-to-Speech in Kotlin Multiplatform, From Scratch

Build a small Android and iOS app that listens to a sentence, turns it into editable text, and reads it back using models running on the device.

The finished app has one screen: **Record**, **Stop & transcribe**, a text field, **Speak**, and **Cancel / stop**. Recording stops automatically after 20 seconds. The English voice accepts up to 500 characters. Once the models are packaged, inference works without an internet connection.

I wanted an example small enough to understand without bringing along a larger application's architecture. This project has no CaraML dependency, database, dependency injection framework, model hub, or server. It also avoids Android's `SpeechRecognizer` and system text-to-speech services: we choose and package the actual models ourselves.

The complete source is in [KmpOfflineSpeech](https://github.com/Debanshu777/KmpOfflineSpeech). The snippets below explain the important connections; the repository includes the complete platform adapters and build files.

## What we are integrating

There are three separate pieces:

- **Whisper tiny**, exported to INT8 ONNX, recognizes speech.
- **KittenTTS Nano v0.8 INT8** generates English speech.
- **sherpa-onnx** loads those model files and handles speech-specific inference using ONNX Runtime.

A model file contains learned weights. A runtime is executable code that knows how to run them. Adding an ONNX file to a project does not add an inference engine, and adding a runtime does not provide a model.

We use sherpa-onnx's Android AAR and its iOS C API. Kotlin Multiplatform shares the UI, state transitions and operation ownership. Microphone capture, playback and native bindings stay in platform source sets.

## Start with a small project

You need JDK 21 or newer, Python 3.10 or newer, and an Android SDK with API 37. The Android app supports API 26 and above. For iOS, use an Apple Silicon Mac with full Xcode, its command-line tools and an iOS 18.5 or newer simulator or device. Device deployment also needs Apple signing configured.

The checked-in Gradle wrapper and build files pin the toolchain. Prefer those files over replacing versions independently while following the tutorial.

```bash
git clone https://github.com/Debanshu777/KmpOfflineSpeech.git
cd KmpOfflineSpeech
python3 scripts/setup.py --platform android
./gradlew :androidApp:assembleDebug
```

Set `ANDROID_HOME` or create `local.properties` with your SDK location. Open the project in Android Studio and run `androidApp`, or install the generated APK:

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n dev.debanshu.speech.sample/dev.debanshu.speech.android.MainActivity
```

For iOS, download its runtimes, compile the small audio bridge, then open the included host:

```bash
python3 scripts/setup.py --platform ios
python3 scripts/prepare_ios.py
open iosApp/iosApp.xcodeproj
```

Choose the **OfflineSpeech** scheme and an iOS simulator. Xcode's build phase invokes Gradle to build the shared framework. The bridge preparation uses `xcrun`; standalone command-line tools without full Xcode are insufficient.

The useful part of the tree is compact:

```text
androidApp/                         Android launcher
composeApp/src/
  commonMain/                       Compose UI, controller, platform contract
  commonTest/                       Controller behavior tests
  androidMain/                      Android audio and sherpa Kotlin API
  iosMain/kotlin/                   iOS audio adapter and direct C API calls
  iosMain/cinterop/                 C header, definition, audio bridge
iosApp/                             SwiftUI host and Xcode project
scripts/                            Pinned downloads and iOS bridge build
models/                             Generated model files
libs/ios/                           Generated native frameworks
```

The JVM target runs shared controller tests; it is not a desktop speech app.

## Wire the platform dependencies

From an empty Compose Multiplatform Android/iOS project, keep the shared code in `composeApp` and the Android launcher in `androidApp`. The shared module applies Kotlin Multiplatform, the Compose compiler/plugin, and `com.android.kotlin.multiplatform.library`. Use the pinned [root build file](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/build.gradle.kts) for compatible plugin versions.

The Android-specific runtime dependency is simply:

```kotlin
kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(files("libs/sherpa-onnx-2.0.1.aar"))
        }
    }
}
```

The AAR already carries its native runtime. This example does not need another ONNX Runtime Maven dependency or a custom Android CMake build. The [shared module build file](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/build.gradle.kts) includes the Compose, coroutine and lifecycle dependencies used by the screen.

## Prepare the weights and runtimes

[`scripts/dependencies.json`](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/scripts/dependencies.json) records download URLs, SHA-256 checksums and model sizes. The setup script verifies cached downloads and new responses before installing them. Model archives are extracted with path and file-type checks.

Whisper needs three files: `tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx` and `tiny-tokens.txt`. Kitten needs `model.int8.onnx`, `voices.bin`, `tokens.txt` and `lexicon-en-us.txt`. The lexicon is a separate download; the ONNX file alone is incomplete.

Android gets an AAR under `composeApp/libs` and model assets under `composeApp/src/androidMain/assets/models`. Before inference, the adapter copies assets to app-private files because its model configuration uses filesystem paths. iOS links static XCFrameworks and includes the root `models` directory as a folder resource.

These generated files stay out of Git. Packaging weights makes the sample larger, but removes runtime download screens and lets the app start offline after installation. A production app can make model installation a separate feature.

For iOS we follow the exact binary URLs in the tagged upstream package manifests. The [sherpa-onnx v2.0.1 manifest](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/Package.swift) references ONNX Runtime's `1.28.2` package; that [package manifest](https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/Package.swift) points to `1.28.1` binary archives. The lock preserves that distinction.

## Share a contract, keep ownership explicit

The shared audio value carries both samples and their real sample rate:

```kotlin
data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
)
```

Samples are mono float PCM, normally between `-1f` and `1f`. Sample rate tells us how many values represent one second. Relabelling a 48 kHz recording as 16 kHz changes its timing; it does not resample it.

The platform boundary is deliberately small:

```kotlin
interface SpeechPlatform {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun startRecording()
    suspend fun stopRecording(): PcmAudio
    suspend fun transcribe(audio: PcmAudio): String
    suspend fun synthesize(text: String): PcmAudio
    suspend fun play(audio: PcmAudio)
    suspend fun stop()
    suspend fun close()
}
```

[`SpeechController`](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/commonMain/kotlin/dev/debanshu/speech/SpeechController.kt) owns operations. It exposes a `StateFlow` with phases such as Recording, Transcribing, Synthesizing and Playing. A mutex prevents teardown from overlapping native model work. Compose observes this state and disables conflicting actions.

Each inference call creates and releases its own model session. That is easy to follow and avoids keeping two models resident between operations, though repeated model loading adds startup work. A longer-lived application could cache sessions under the same ownership rules.

## Android: configure Whisper and Kitten

The [Android adapter](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/androidMain/kotlin/dev/debanshu/speech/AndroidSpeechPlatform.kt) uses the Kotlin classes included in sherpa-onnx's AAR. We do not write a JNI bridge ourselves.

Whisper's model configuration connects all three files:

```kotlin
OfflineModelConfig(
    whisper = OfflineWhisperModelConfig(
        encoder = encoderPath,
        decoder = decoderPath,
        language = "en",
        task = "transcribe",
    ),
    tokens = tokensPath,
    numThreads = 2,
    provider = "cpu",
)
```

Pass this to `OfflineRecognizerConfig`, then construct `OfflineRecognizer`. With a recording available, the core sequence is:

```kotlin
val stream = recognizer.createStream()
try {
    stream.acceptWaveform(audio.samples, audio.sampleRate)
    recognizer.decode(stream)
    currentCoroutineContext().ensureActive()
    recognizer.getResult(stream).text.trim()
} finally {
    stream.release()
}
```

The surrounding `finally` also releases the recognizer. Run creation and decoding on `Dispatchers.Default`, away from the UI thread.

Kitten uses a different configuration, even though its weights are also ONNX:

```kotlin
OfflineTtsKittenModelConfig(
    model = modelPath,
    voices = voicesPath,
    tokens = tokensPath,
    lexicon = lexiconPath,
)
```

Place that inside `OfflineTtsModelConfig` and `OfflineTtsConfig`, select the CPU provider, then create `OfflineTts`. Synthesis returns samples and their output rate:

```kotlin
val generated = tts.generate(text, sid = 0, speed = 1.0f)
val audio = PcmAudio(generated.samples, generated.sampleRate)
```

The demo selects speaker zero on both platforms. Release the TTS session in `finally`, and use the returned rate for playback.

## iOS: call the C API from Kotlin

On iOS, Kotlin/Native generates bindings from a small header that includes sherpa-onnx's C API and declares our audio functions. Its `.def` file sets the generated Kotlin package. Gradle adds the appropriate framework header directory for device or simulator and links sherpa, ONNX Runtime, the audio bridge and Apple frameworks.

The definition file is only two lines:

```text
headers = speech_audio.h
package = dev.debanshu.speech.native
```

For each iOS target, the cinterop task receives the bridge and selected XCFramework header directories:

```kotlin
target.compilations.getByName("main").cinterops.create("speechNative") {
    defFile("src/iosMain/cinterop/speech.def")
    compilerOpts(
        "-I${file("src/iosMain/cinterop")}",
        "-I$sherpa/SherpaOnnxC.framework/Headers",
    )
}
```

Here `sherpa` is the selected `ios-arm64` or `ios-arm64_x86_64-simulator` slice directory. The full build and Xcode host also link `SherpaOnnxC`, `onnxruntime`, the compiled audio archive and Apple frameworks; a static Kotlin framework does not remove the host's final link requirements.

This distinction matters: **cinterop generates bindings; linking supplies implementations**. Having generated Kotlin functions does not mean the native libraries have been linked.

The [iOS adapter](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/iosMain/kotlin/dev/debanshu/speech/IosSpeechPlatform.kt) allocates and zeroes the C configuration before filling fields:

```kotlin
memScoped {
    val config = alloc<SherpaOnnxOfflineRecognizerConfig>()
    memset(config.ptr, 0,
        sizeOf<SherpaOnnxOfflineRecognizerConfig>().convert())
    config.feat_config.sample_rate = 16000
    config.feat_config.feature_dim = 80
    config.model_config.whisper.encoder = encoderPath.cstr.ptr
    config.model_config.whisper.decoder = decoderPath.cstr.ptr
    config.model_config.tokens = tokensPath.cstr.ptr
    // The complete adapter also sets language, task and CPU options.
}
```

Create a recognizer with `SherpaOnnxCreateOfflineRecognizer`, create its stream, then pass the recording:

```kotlin
audio.samples.usePinned { pinned ->
    SherpaOnnxAcceptWaveformOffline(
        stream, audio.sampleRate,
        pinned.addressOf(0), audio.samples.size,
    )
}
SherpaOnnxDecodeOfflineStream(recognizer, stream)
```

`usePinned` keeps the Kotlin array's address stable for that call. Do not retain the pointer after the block. Convert the recognition result to a Kotlin string before destroying the native result, then destroy the stream and recognizer in reverse ownership order. [Kotlin's C interop documentation](https://kotlinlang.org/docs/native-c-interop.html) explains these pointer lifetimes.

TTS follows the same pattern: zero the config, fill `config.model.kitten`, create the TTS handle, generate with speaker zero, copy the returned PCM into a Kotlin `FloatArray`, then destroy the native generated audio and TTS handle. Copying before freeing is essential; a C pointer is not an independently owned Kotlin array.

## Move audio without changing its meaning

Android captures mono PCM16 with `AudioRecord`, trying 16 kHz and falling back to 48 kHz or 44.1 kHz when needed. It checks initialization and read errors rather than assuming every microphone route supports 16 kHz. [Android's AudioRecord reference](https://developer.android.com/reference/android/media/AudioRecord) documents these constraints.

Convert each captured short with:

```kotlin
samples[index] = pcm16[index] / 32768f
```

The iOS Objective-C++ bridge uses AVFoundation to capture float PCM at the input's actual rate and mixes input channels to mono. Both adapters keep that rate attached to the recording. sherpa-onnx resamples input for Whisper internally, as shown in its [offline stream implementation](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/csrc/offline-stream.cc). We do not implement our own resampler.

Playback takes the opposite path. Android streams float PCM to `AudioTrack` and waits for queued frames to finish. iOS packages the generated samples for `AVAudioPlayer`. Neither assumes the voice's output rate matches the microphone or Whisper input rate.

Android declares and requests `RECORD_AUDIO`. iOS includes `NSMicrophoneUsageDescription` and requests recording permission; [Apple requires this purpose string](https://developer.apple.com/documentation/bundleresources/information-property-list/nsmicrophoneusagedescription). Denial becomes a visible error rather than opening the recorder anyway.

## Handle stopping and leaving the screen

Recording, inference and playback are different operations. The shared controller stops and transcribes at 20 seconds, rejects recordings shorter than 0.2 seconds, and limits synthesis input to 500 characters.

When the app leaves the foreground, `LifecycleEventEffect(ON_STOP)` asks the controller to stop. `DisposableEffect` calls `close()` when the UI is disposed. Native cleanup waits until the current owner releases the mutex.

Cancellation has a practical limit: a synchronous native decode already running may continue until it returns. Cancelling its coroutine prevents a stale result from being shown; it does not instantly interrupt the CPU operation. Destroying the recognizer underneath that operation would be unsafe. The UI therefore has a Stopping phase while ownership is resolved.

This sample handles short foreground interactions. It does not provide background recording, streaming partial transcripts, automatic route recovery or interruption recovery. After an audio interruption, stop and retry. Those features need additional platform behavior.

## Verify the result, including offline use

Run the shared behavior tests and setup-script checks:

```bash
./gradlew :composeApp:jvmTest
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

The Android native roundtrip test was run on a Pixel 9 with Android 17: Kitten generated 60,010 samples at 24 kHz, and Whisper returned “The quick brown fox jumps over the lazy dog.” This checks the packaged models and JNI, independently of the microphone or speaker. See the [recorded evidence](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/androidApp/verification/native-smoke-2026-11-21.json).

An equivalent native C API roundtrip also passed on the iPhone 17 / iOS 27 simulator, with the same sentence and a 24 kHz output rate. The shared iOS app was built and visually launched. The [iOS notes](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/docs/ios-notes.md) include a reproducible smoke command; neither roundtrip tests the physical microphone or speaker.

Then verify each target you intend to support:

1. Install with models packaged, launch and allow microphone access.
2. Say a short English sentence and tap **Stop & transcribe**.
3. Edit the text and tap **Speak**. Confirm you hear the edited version.
4. Try **Cancel / stop**, automatic recording timeout, and leaving the app.
5. Deny microphone permission and confirm the app remains usable for synthesis.
6. Disable Wi-Fi and cellular connectivity, relaunch, then repeat transcription and playback.

Successful compilation and mocked controller tests do not establish microphone quality, recognition accuracy or audible output on a device. Record the actual devices and tests when reporting results. This tutorial makes no latency or quality benchmark claim.

## Keep the licenses with the integration

Runtime and model licenses are separate. sherpa-onnx uses [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/LICENSE), Whisper uses [MIT](https://github.com/openai/whisper/blob/main/LICENSE), and the selected [Kitten Nano INT8 model card](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8) identifies Apache-2.0. Preserve applicable notices from redistributed native binaries, model exports and supporting assets too.

The reusable idea is the boundary: shared Kotlin controls the interaction and owns operations; platform adapters supply audio and native inference. Starting with this small app makes every file in that boundary visible. From there, you can add model installation, longer recordings or cached sessions without needing to adopt someone else's application infrastructure.
