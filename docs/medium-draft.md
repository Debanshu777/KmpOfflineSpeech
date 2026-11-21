# Offline Speech Recognition and Text-to-Speech in Kotlin Multiplatform, From Scratch

A microphone, a text field, and two local models are enough to build a useful speech demo. In this tutorial we will create an Android and iOS app that records a sentence, transcribes it, lets us edit the text, and reads it aloud.

We will start with an empty project. Kotlin Multiplatform will share the screen and operation controller. Android and iOS will supply audio capture, playback, and their native model bindings. The [finished project](https://github.com/Debanshu777/KmpOfflineSpeech) is available for comparing files as you go.

The demo uses **Whisper tiny INT8 ONNX** for speech recognition and **KittenTTS Nano v0.8 INT8** for English synthesis, through **sherpa-onnx**. It has no CaraML dependency, database, dependency injection framework, model hub, or server. We choose and package the weights ourselves instead of using system speech recognition or system TTS.

## 1. Create the project and its two modules

Install JDK 21 or newer, an Android SDK with platform 37, and Gradle for generating the wrapper once. For iOS, use an Apple Silicon Mac with full Xcode and an iOS 18.5 or newer simulator or device. Physical iOS deployment also needs signing configured.

Create an empty directory, then generate its wrapper:

```bash
mkdir KmpOfflineSpeech
cd KmpOfflineSpeech
gradle wrapper --gradle-version 9.9.1
```

Use `./gradlew` afterward so everyone builds with the same Gradle release. Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "KmpOfflineSpeech"
include(":composeApp", ":androidApp")
```

In the root `build.gradle.kts`, declare the plugins:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.1" apply false
    id("com.android.application") version "9.5.1" apply false
}
```

Add `android.useAndroidX=true` and `kotlin.mpp.enableCInteropCommonization=true` to `gradle.properties`. Point Gradle at your Android SDK through `ANDROID_HOME` or `local.properties`.

Create these source directories:

```text
androidApp/src/main/                 Android Activity and manifest
composeApp/src/commonMain/kotlin/    Contract, controller, Compose screen
composeApp/src/androidMain/kotlin/   Android adapter
composeApp/src/iosMain/kotlin/       iOS adapter and UIViewController
composeApp/src/iosMain/cinterop/     Native header and audio bridge
buildSrc/src/main/kotlin/            Typed dependency-preparation tasks
iosApp/                             SwiftUI host
```

Use `dev.debanshu.speech` for the shared example package, with matching directories under each Kotlin source set. `composeApp` is the reusable shared library; `androidApp` is its Android launcher.

## 2. Configure the shared library and Android host

Apply the multiplatform, Compose, Compose compiler, and Android multiplatform library plugins in `composeApp/build.gradle.kts`. Configure Android and the two Apple Silicon targets:

```kotlin
kotlin {
    android {
        namespace = "dev.debanshu.speech.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
    }
    iosArm64()
    iosSimulatorArm64()
    jvm() // Shared controller tests; no desktop speech implementation.
}
```

Add Compose runtime, foundation, UI and Material 3 to `commonMain`, along with coroutines and lifecycle's Compose integration. The sample pins Compose `1.12.2`, Material 3 `1.10.0-alpha05`, coroutines `1.11.0`, and lifecycle `2.11.0`. Add Activity Compose `1.13.0` to `androidMain`. Keep these versions together while constructing the example.

In `androidApp/build.gradle.kts`, apply `com.android.application` and the Compose compiler plugin. Set the namespace to `dev.debanshu.speech.android`, application ID to `dev.debanshu.speech.sample`, compile SDK to 37, minimum SDK to 26, and target SDK to 36. Enable Compose, select Java 17 bytecode, and depend on `project(":composeApp")` plus Activity Compose. Include `arm64-v8a` and `x86_64` for a phone and emulator.

Keep the host small. Its Activity will construct the platform adapter before the Activity reaches STARTED, because Android's permission launcher must be registered early:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val speech = createAndroidSpeechPlatform(this)
        setContent { SpeechApp(speech) }
    }
}
```

We will implement those two functions shortly.

## 3. Make model installation part of Gradle

A runtime is executable inference code. Weights are the learned data it loads. We need both, plus each model's supporting files.

Whisper needs `tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, and `tiny-tokens.txt`. Kitten needs `model.int8.onnx`, `voices.bin`, `tokens.txt`, and `lexicon-en-us.txt`. The lexicon is a separate download; copying only the ONNX file will not create a working voice.

Store the URLs, exact revisions, sizes and SHA-256 checksums in `scripts/dependencies.json`. Pin sherpa-onnx `2.0.1` and the exact model exports. Hashes are what let a repeated build reject changed or incomplete content.

Create `buildSrc/build.gradle.kts` with the `kotlin-dsl` plugin, `mavenCentral()`, Commons Compress `1.28.0` for archives, and Gson `2.13.2` for the manifest. Name this build `speech-build-tools` in `buildSrc/settings.gradle.kts`.

Put download and extraction helpers in `SpeechArtifacts.kt`. Stream bytes into a temporary file, compute SHA-256, check the expected size and hash, and only then publish the file. The core of the helper is:

```kotlin
val temporary = Files.createTempFile(cache.toPath(), ".speech-", ".part").toFile()
try {
    open(artifact.json["url"].asString).use { input ->
        temporary.outputStream().use { input.copyTo(it) }
    }
    verify(temporary, artifact) // Expected byte count and streaming SHA-256.
    move(temporary.toPath(), output.toPath())
    return output
} finally {
    temporary.delete()
}
```

Verify cached files too. During extraction, reject paths escaping the destination, links, duplicate entries, and archives exceeding a chosen expansion limit. The [complete helper](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/buildSrc/src/main/kotlin/SpeechArtifacts.kt) contains those checks.

The [build tools notes](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/buildSrc/README.md) explain the task inputs, verified cache and extraction limits alongside the implementation.

Create typed `PrepareSpeechModels`, `PrepareAndroidSpeech`, `PrepareIosSpeech`, and `PrepareIosAudio` tasks. Declare the manifest as `@InputFile`, the cache as `@LocalState`, and generated files/directories as outputs. Model preparation downloads Whisper and the Kitten archive, verifies the extracted Kitten files, and publishes `models/whisper` and `models/kitten`.

Register the model task in the root build:

```kotlin
val prepareSpeechModels = tasks.register<PrepareSpeechModels>("prepareSpeechModels") {
    manifestFile.set(layout.projectDirectory.file("scripts/dependencies.json"))
    downloadCache.set(layout.projectDirectory.dir(".cache/downloads"))
    offline.set(gradle.startParameter.isOffline)
    modelDirectory.set(layout.projectDirectory.dir("models"))
}
```

Register `prepareAndroidSpeech` with `modelDirectory` supplied by `prepareSpeechModels.flatMap { it.modelDirectory }`. This provider carries the task dependency. Register `prepareIosSpeech` with a dependency on model preparation. Keep network access inside task actions, so opening the project or running controller tests does not download models.

Downloads, model extraction, and native audio compilation all belong to Gradle's task graph. One build command can prepare its prerequisites without a separate scripting environment or manual setup command.

Android's preparation copies the AAR to `composeApp/libs/sherpa-onnx-2.0.1.aar` and models to `composeApp/build/generated/speechAssets/models`. Register that generated asset directory and add the AAR to `androidMain`:

```kotlin
val prepareAndroidSpeech = rootProject.tasks
    .named<PrepareAndroidSpeech>("prepareAndroidSpeech")
// In androidMain.dependencies:
implementation(files(prepareAndroidSpeech.flatMap { it.runtimeAar })
    .builtBy(prepareAndroidSpeech))
```

Use Android Components' `onVariants` callback to register the generated assets with `variant.sources.assets?.addGeneratedSourceDirectory(prepareAndroidSpeech, PrepareAndroidSpeech::assetsDirectory)`. Both the AAR and assets now tell Gradle which task produces them.

iOS preparation installs static sherpa and ONNX Runtime XCFrameworks under `libs/ios` and keeps a `models` folder for the application bundle. Ignore downloaded runtimes, models, and caches in Git.

The iOS binary lock follows upstream's [sherpa package manifest](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/Package.swift). Its ONNX Runtime package version is `1.28.2`, while that [package's manifest](https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/Package.swift) references `1.28.1` binary archives. Keep the actual URLs and hashes instead of guessing filenames from version labels.

## 4. Define shared audio and operations

Create `SpeechPlatform.kt` in `commonMain`:

```kotlin
data class PcmAudio(val samples: FloatArray, val sampleRate: Int)

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

Use mono float PCM in the range `-1f` to `1f`, and validate that the samples are nonempty and the rate is positive. A sample rate describes how many values represent one second. Relabelling 48 kHz input as 16 kHz changes its meaning; it does not resample the audio.

One platform instance belongs to one controller. Model operations and teardown must never overlap. That ownership rule is more important than whether we use an interface or `expect`/`actual`.

## 5. Implement Android recognition and synthesis

Create `AndroidSpeechPlatform.kt` in `androidMain`. Keep the application context for assets/files, and register `ActivityResultContracts.RequestPermission()` with the Activity for microphone permission.

Copy model assets to `context.filesDir/models` before opening sessions, because the configurations below use filesystem paths. Copy through a temporary file and close both streams. Do that work on `Dispatchers.IO`.

Whisper configuration connects the encoder, decoder and tokens:

```kotlin
val recognizer = OfflineRecognizer(
    config = OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = encoderPath,
                decoder = decoderPath,
                language = "en",
                task = "transcribe",
            ),
            tokens = tokensPath,
            numThreads = 2,
            provider = "cpu",
        ),
    ),
)
```

Use the upstream `com.k2fsa.sherpa.onnx` classes from the AAR; no custom JNI code is needed. Run creation and decoding on `Dispatchers.Default`:

```kotlin
try {
    val stream = recognizer.createStream()
    try {
        stream.acceptWaveform(audio.samples, audio.sampleRate)
        recognizer.decode(stream)
        currentCoroutineContext().ensureActive()
        recognizer.getResult(stream).text.trim()
    } finally { stream.release() }
} finally { recognizer.release() }
```

For synthesis, construct `OfflineTtsConfig` containing an `OfflineTtsModelConfig` with its `kitten` field:

```kotlin
OfflineTtsKittenModelConfig(
    model = modelPath,
    voices = voicesPath,
    tokens = tokensPath,
    lexicon = lexiconPath,
)
```

Set two CPU threads and `maxNumSentences = 1`, then create `OfflineTts`. Generate with `tts.generate(text, sid = 0, speed = 1.0f)`. Return `PcmAudio(generated.samples, generated.sampleRate)` and release the TTS session in `finally`.

Creating a session per operation keeps this example's ownership simple. It adds model-loading work each time. Session caching can come later, with the same serialization and cleanup rules.

## 6. Add Android microphone capture and playback

Create an `AudioRecord` for mono PCM16. Try 16 kHz, then 48 kHz and 44.1 kHz when necessary. Check `getMinBufferSize`, `STATE_INITIALIZED`, and negative read results; [Android does not guarantee every requested rate on every route](https://developer.android.com/reference/android/media/AudioRecord).

Read on an IO coroutine into a bounded buffer. Convert each short using:

```kotlin
samples[index] = pcm16[index] / 32768f
```

Allocate at most `actualSampleRate * 20` samples. On stop, stop the recorder, join its reader, release it, and return the captured portion with the actual sample rate.

Feed that pair to sherpa. Its [offline stream implementation](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/csrc/offline-stream.cc) resamples incoming audio for Whisper, so we do not need our own resampler.

For playback, build an `AudioTrack` with mono `ENCODING_PCM_FLOAT` at the **synthesis output rate**. Write until all samples are queued, handle partial/zero writes, and wait until `playbackHeadPosition` reaches the queued frame count. A successful write means queued audio, not completed playback. Stop and release the track in `finally`.

In the Android host manifest, declare microphone access and export the launcher Activity:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-feature android:name="android.hardware.microphone"
    android:required="true" />
```

Request permission before opening the recorder. Denial should produce a readable error while leaving text-to-speech available.

## 7. Generate iOS C bindings and link the native libraries

Create `speech_audio.h` in `iosMain/cinterop`. Include sherpa's header and declare C functions for your audio bridge:

```c
#include "sherpa-onnx/c-api/c-api.h"
void *speech_capture_create(void);
float *speech_capture_stop(void *capture, int32_t *count, int32_t *rate);
void speech_free_samples(float *samples);
```

Also declare start/destroy functions, playback functions and a microphone permission callback. Wrap declarations in `extern "C"` when compiling as C++.

Create `speech.def`:

```properties
headers = speech_audio.h
package = dev.debanshu.speech.native
```

For each iOS target in the shared Gradle build, select `ios-arm64` or `ios-arm64_x86_64-simulator`. Register cinterop with include paths for our header and the selected sherpa framework's Headers directory. Export a static Kotlin framework named `SpeechApp`.

Its linker options must include sherpa, ONNX Runtime, our audio archive and Apple dependencies:

```kotlin
linkerOpts(
    "-F$sherpa", "-framework", "SherpaOnnxC",
    "-F$ort", "-framework", "onnxruntime",
    "-L$audio", "-lspeech_audio", "-lc++",
    "-framework", "AVFoundation", "-framework", "AudioToolbox",
    "-framework", "Foundation", "-framework", "CoreML",
    "-framework", "CoreFoundation",
)
```

Bindings and linking are separate steps: cinterop makes Kotlin declarations; the linker supplies their implementations.

In `IosSpeechPlatform.kt`, opt into `ExperimentalForeignApi`. Allocate and zero the C recognizer config in `memScoped`, set Whisper's file paths, English language, transcription task and CPU options, then call `SherpaOnnxCreateOfflineRecognizer`.

After creating a stream, pass Kotlin samples safely:

```kotlin
audio.samples.usePinned { pinned ->
    SherpaOnnxAcceptWaveformOffline(
        stream, audio.sampleRate,
        pinned.addressOf(0), audio.samples.size,
    )
}
SherpaOnnxDecodeOfflineStream(recognizer, stream)
```

Copy the result's text to a Kotlin string, then destroy the native result, stream and recognizer in `finally`. For Kitten, fill `config.model.kitten`, generate with speaker zero, copy returned samples and sample rate into `PcmAudio`, then destroy generated audio and the TTS handle.

Never retain a pinned pointer beyond its block or read native output after freeing it. [Kotlin's C interop guide](https://kotlinlang.org/docs/native-c-interop.html) explains these lifetimes and callback `StableRef` disposal.

## 8. Supply iOS audio and the SwiftUI host

Implement `speech_audio.mm` using AVFoundation. An `AVAudioEngine` input tap captures float PCM at the hardware rate. Average input channels to mono and cap native storage at 20 seconds, independently of the UI timer. On stop, remove the tap and return a copied buffer plus its actual rate. Kotlin copies that buffer and calls `speech_free_samples`.

For playback, the small bridge converts generated floats to a PCM WAV buffer and uses `AVAudioPlayer`. Configure the audio session for recording or spoken playback as appropriate and deactivate it after stopping. Keep audio handle calls on Main; blocking model work runs on Default.

Use a typed Gradle task to invoke Xcode's `clang++` and `libtool` for device and simulator, producing `libspeech_audio.a` for each. Register it as `prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64`, depending on `prepareIosSpeech`. Make cinterop processing and framework linking depend on the corresponding audio task. The bridge contains audio code only; inference calls the upstream C API directly from Kotlin.

Create an iOS SwiftUI application target. Add `models` as a **folder resource**, preserving paths such as `models/whisper/tiny-tokens.txt`. Add a build phase before Swift compilation:

```bash
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

Link the resulting `SpeechApp.framework` and add its build directory to Framework Search Paths. Disable User Script Sandboxing for this Gradle phase. Set the host and audio bridge deployment target to 18.5; this pinned Compose toolchain includes an ICU dependency requiring that minimum.

Add these `Info.plist` entries:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Record a short clip for speech recognition on this device.</string>
<key>CADisableMinimumFrameDurationOnPhone</key>
<true/>
```

The microphone purpose string is [required by Apple](https://developer.apple.com/documentation/bundleresources/information-property-list/nsmicrophoneusagedescription). The frame-duration entry is part of this Compose iOS host's rendering configuration.

Expose `MainViewController()` using `ComposeUIViewController { SpeechApp(remember { IosSpeechPlatform() }) }`. In Swift, import `SpeechApp` and return `MainViewControllerKt.MainViewController()` from a `UIViewControllerRepresentable` embedded in your app's `WindowGroup`.

## 9. Connect the shared controller and screen

Create phases for Idle, Starting, Recording, Transcribing, Synthesizing, Playing and Stopping. Expose a `StateFlow<SpeechUiState>` containing phase, editable text and error. The controller holds a `Job?` for the active operation and a `Mutex` for ownership.

Run actions through the same guarded function. Its cleanup must still run when a coroutine has been cancelled:

```kotlin
private fun runOperation(block: suspend () -> Unit) {
    operation = scope.launch {
        ownership.withLock {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = state.value.copy(
                    phase = Phase.Idle, error = error.message ?: "Speech failed")
            } finally {
                if (!isActive || state.value.error != null) {
                    withContext(NonCancellable) { platform.stop() }
                }
            }
        }
    }
}
```

The transcription action stops capture, checks for at least 0.2 seconds of audio, calls the adapter, and updates the text. Start a separate timer when recording begins: `delay(20_000); transcribe()`.

The speaking action shows the same ownership pattern:

```kotlin
val text = state.value.text.take(500)
mutableState.value = state.value.copy(phase = Phase.Synthesizing)
runOperation {
    val audio = platform.synthesize(text)
    try {
        mutableState.value = state.value.copy(phase = Phase.Playing)
        platform.play(audio)
        mutableState.value = state.value.copy(phase = Phase.Idle)
    } finally { audio.samples.fill(0f) }
}
```

In Compose, remember a controller with its own Main scope, collect its state, and connect the controls:

```kotlin
val controller = remember(platform) {
    SpeechController(platform,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}
val state by controller.state.collectAsState()
Button(onClick = controller::speak,
    enabled = state.phase == Phase.Idle && state.text.isNotBlank()) {
    Text("Speak")
}
BasicTextField(
    value = state.text, onValueChange = controller::edit,
    enabled = state.phase == Phase.Idle, minLines = 3,
    textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
)
LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop() }
DisposableEffect(controller) { onDispose { controller.close() } }
```

Add Record/Stop-and-transcribe and Cancel buttons, and disable editing during operations. Surface permission and model errors in the screen. The controller's scope stays alive long enough to perform ordered cleanup after the composition is removed, then cancels itself.

Keep the recording control, editable transcript and playback control visually distinct. Display actual recording time against the 20-second limit instead of inventing microphone levels. Keep the text editor and playback control together, with scrolling and keyboard insets for smaller screens. The [shared Compose screen](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/commonMain/kotlin/dev/debanshu/speech/SpeechApp.kt) provides the complete presentation layer; it can be styled without changing the native integration.

Cancellation does not instantly interrupt synchronous native inference. Cancel the coroutine, discard results after native work returns, and wait for ownership before destroying handles. The Stopping state communicates that wait. This sample supports short foreground clips; route recovery, background recording and streaming transcripts require more platform work.

## 10. Build, run and check offline behavior

Build Android from the root. Assembly brings in its preparation tasks automatically:

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n dev.debanshu.speech.sample/dev.debanshu.speech.android.MainActivity
```

For iOS, prepare its files and simulator audio bridge, then open the host:

```bash
./gradlew prepareIosSpeech prepareIosAudioIosSimulatorArm64
open iosApp/iosApp.xcodeproj
```

Select the OfflineSpeech scheme and an iOS 18.5+ simulator. The Xcode Gradle phase builds the Kotlin framework. Device builds select the device audio preparation task automatically.

Add `kotlin("test")` and coroutines-test to `commonTest`. Useful controller tests cover denied permission, the recording timeout, and cancellation waiting for native ownership before teardown. Add checksum/cache/archive tests in `buildSrc`, and expose them through `checkSpeechSetup`:

```bash
./gradlew :composeApp:jvmTest checkSpeechSetup
```

Run shared controller tests on the JVM without native libraries, then build/install Android or open the iOS host. On each actual target, record a short English sentence, transcribe it, edit it, and speak the edited version. Try cancellation, permission denial, automatic timeout and leaving the screen.

Finally disable Wi-Fi and cellular connectivity, relaunch the installed app, and repeat both model operations. Dependencies are downloaded during preparation, while the application performs local inference using packaged assets.

The Gradle preparation regressions passed, along with Android compilation and the iOS simulator host build. On a Pixel 9 running Android 17, the acoustic test exercised the actual app adapter: Kitten generated speech, AudioTrack played it, AudioRecord captured the speaker through the microphone, and Whisper transcribed that capture. It returned “The quick round fox jumps over the lazy dog.” The original sentence used “brown,” so the result was not word-perfect. The [recorded evidence](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/androidApp/verification/acoustic-roundtrip-2026-11-21.json) preserves both observed runs, the non-silence checks and the test's acceptance criterion. A separate [iOS C API roundtrip](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/docs/ios-native-smoke.json) verifies the native model pair on the simulator; it does not test an iPhone microphone or speaker. These are integration checks, not speech-accuracy or latency benchmarks.

Successful compilation does not prove audible output or microphone recognition on hardware. Keep any measured device results separate from assumptions about accuracy or speed.

Preserve licenses when distributing the app: sherpa-onnx uses [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/LICENSE), Whisper uses [MIT](https://github.com/openai/whisper/blob/main/LICENSE), and the selected [Kitten Nano INT8 model](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8) identifies Apache-2.0. Native binaries and supporting model assets can carry additional notices.

You now have the full path: microphone PCM, local recognition, shared editable state, local synthesis, and platform playback. The shared boundary stays small enough to move into another KMP app. The [complete project](https://github.com/Debanshu777/KmpOfflineSpeech) supplies the surrounding error paths and build wiring when you want to compare your implementation.
