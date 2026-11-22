# Run Speech-to-Text and Text-to-Speech Models on device in Kotlin Multiplatform

Load Whisper and KittenTTS on Android, iOS and desktop, then connect them to a small Compose screen.

I wanted a small demo that could record a sentence, turn it into editable text, and read it back on the device. This is the speech integration behind that demo, starting from an existing Kotlin Multiplatform app.

We’ll use Whisper for recognition, KittenTTS for synthesis, and sherpa-onnx to run both. First, I’ll walk through the model files, platform calls and a small UI. The [complete project](https://github.com/Debanshu777/Siren) is available alongside the guide. At the end, I’ll show how Siren turns this wiring into an SDK you can reuse.

![Record, transcribe and play in the actual Android app](images/siren-demo.gif)

*The actual Android app. For this repeatable recording, the phone’s microphone captures a synthesized sentence played through its speaker.*

## 1. Get the model files

This sample uses **Whisper tiny multilingual INT8 ONNX**, configured for English, and **KittenTTS Nano v0.8 INT8**, an English voice. Keep their supporting files together:

```text
models/
  whisper/
    tiny-encoder.int8.onnx
    tiny-decoder.int8.onnx
    tiny-tokens.txt
  kitten/
    model.int8.onnx
    voices.bin
    tokens.txt
    lexicon-en-us.txt
```

Download Whisper’s encoder, decoder and tokens from this [pinned export](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c). Kitten needs the [Nano archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2) plus the separate [English lexicon](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt). The ONNX file alone isn’t enough: Kitten also needs its voice data, tokens and lexicon.

## 2. Prepare the runtimes

Android uses the [sherpa-onnx 2.0.1 AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar), which includes its native runtime. On iOS, use `SherpaOnnxC.xcframework` and the matching `onnxruntime.xcframework` referenced by the [tagged package manifest](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/Package.swift).

I’ve kept preparation in Gradle. The companion project has two parts:

```text
siren/                 # model configuration, inference and native runtime
sample/composeApp/     # Compose UI, controller, microphone and playback
sample/androidApp/     # Android host
sample/iosApp/         # iOS host
sample/desktopApp/     # desktop JVM host
```

The SDK has no UI dependency and ships no model weights. The sample selects its own files. These tasks prepare the runtime and the sample's assets:

```bash
./gradlew prepareAndroidRuntime prepareAndroidSpeech
./gradlew prepareIosSpeech prepareSpeechModels
./gradlew prepareJvmRuntime
```

For your own project, bring over the [dependency manifest](https://github.com/Debanshu777/Siren/blob/main/scripts/dependencies.json), the [typed preparation tasks](https://github.com/Debanshu777/Siren/tree/main/buildSrc), and their [root build registration](https://github.com/Debanshu777/Siren/blob/main/build.gradle.kts). They verify downloads before extraction. On Android, the runtime task extracts the Kotlin wrapper JAR and supported JNI libraries from the upstream AAR. In the SDK build:

```kotlin
val runtime = rootProject.tasks
    .named<PrepareAndroidRuntime>("prepareAndroidRuntime")
// Inside androidMain.dependencies:
implementation(files(runtime.flatMap { it.classesJar }).builtBy(runtime))
// Inside KotlinMultiplatformAndroidComponentsExtension.onVariants:
variant.sources.jniLibs?.addGeneratedSourceDirectory(
    runtime, PrepareAndroidRuntime::jniLibsDirectory)
```

Keep the wrapper's JNI classes with the [consumer rules](https://github.com/Debanshu777/Siren/blob/main/siren/consumer-rules.pro). The [SDK build](https://github.com/Debanshu777/Siren/blob/main/siren/build.gradle.kts) also packages runtime notices. The sample registers `PrepareAndroidSpeech.assetsDirectory` as generated Android assets; on iOS, its host includes the prepared `models` folder as a folder resource. Preserve paths such as `models/whisper/tiny-tokens.txt` and keep downloaded weights out of Git.

## 3. Define the shared speech interface

In `commonMain`, represent a mono recording as samples plus its actual sample rate:

```kotlin
data class PcmAudio(val samples: FloatArray, val sampleRate: Int)
interface SpeechEngine {
    val capabilities: SpeechCapabilities
    suspend fun transcribe(audio: PcmAudio): String
    suspend fun synthesize(text: String): PcmAudio
    suspend fun close()
}
interface SpeechPlatform : SpeechEngine {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun startRecording()
    suspend fun stopRecording(): PcmAudio
    suspend fun play(audio: PcmAudio)
    suspend fun stop()
}
```

`SpeechCapabilities` reports which operations are available and their model labels. Siren keeps PCM in `io.github.debanshu.siren.audio`, inference contracts in `.speech`, and the demo’s UI/audio hosts in `.sample`. The [full engine contract](https://github.com/Debanshu777/Siren/blob/main/siren/src/commonMain/kotlin/io/github/debanshu/siren/speech/SpeechEngine.kt) also defines separate recognition and synthesis providers.

Use float PCM in roughly `-1f..1f`, and reject empty recordings or invalid rates. Keep the captured rate: labelling 48 kHz samples as 16 kHz changes their timing. Pass the original rate to sherpa, which resamples internally.

## 4. Load and run the models on Android

The extracted wrapper provides the `com.k2fsa.sherpa.onnx` classes. Native configuration uses filesystem paths, so copy bundled assets into private app storage on `Dispatchers.IO`, or use caller-supplied local files. Run model creation and inference on `Dispatchers.Default` so they don’t block the screen.

For Whisper, supply the encoder, decoder and token paths:

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

Then create a stream for the recording and decode it:

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

Kitten has a different config, but the same pattern: load, generate, copy the audio value, release.

```kotlin
val tts = OfflineTts(
    config = OfflineTtsConfig(
        model = OfflineTtsModelConfig(
            kitten = OfflineTtsKittenModelConfig(
                model = modelPath,
                voices = voicesPath,
                tokens = tokensPath,
                lexicon = lexiconPath,
            ),
            numThreads = 2,
            provider = "cpu",
        ),
        maxNumSentences = 1,
    ),
)
try {
    val generated = tts.generate(text, sid = 0, speed = 1.0f)
    currentCoroutineContext().ensureActive()
    PcmAudio(generated.samples, generated.sampleRate)
} finally { tts.release() }
```

Use the generated sample rate for playback. This demo creates a session per call, trading repeated loading for straightforward cleanup. The [Android engine](https://github.com/Debanshu777/Siren/blob/main/siren/src/androidMain/kotlin/io/github/debanshu/siren/speech/AndroidSpeechEngine.kt) contains the complete providers and resolves their configured file paths.

## 5. Call the same models from iOS

On iOS, Kotlin/Native calls sherpa’s C API. The SDK’s `sherpa.def` starts with:

```properties
headers = sherpa-onnx/c-api/c-api.h
package = io.github.debanshu.siren.internal.native
linkerOpts = -lc++ -framework Foundation -framework CoreML -framework CoreFoundation -framework Accelerate
```

Select the device or simulator XCFramework slice and pass its headers to cinterop. Siren’s [Gradle wiring](https://github.com/Debanshu777/Siren/blob/main/siren/build.gradle.kts) generates the final definition with `staticLibraries` and `libraryPaths`, embedding both native archives in the resulting KLIB. That lets another Kotlin project link the SDK without referring back to this checkout’s frameworks. The sample’s AVFoundation bridge has its own cinterop and stays outside the SDK.

Resolve model paths under `NSBundle.mainBundle.resourcePath`. Inside `memScoped`, with `ExperimentalForeignApi` enabled, zero the config before filling it:

```kotlin
val config = alloc<SherpaOnnxOfflineRecognizerConfig>()
memset(config.ptr, 0,
    sizeOf<SherpaOnnxOfflineRecognizerConfig>().convert())
config.feat_config.sample_rate = 16000
config.feat_config.feature_dim = 80
config.model_config.whisper.encoder = encoderPath.cstr.ptr
config.model_config.whisper.decoder = decoderPath.cstr.ptr
config.model_config.whisper.language = "en".cstr.ptr
config.model_config.whisper.task = "transcribe".cstr.ptr
config.model_config.tokens = tokensPath.cstr.ptr
config.model_config.num_threads = 2
config.model_config.provider = "cpu".cstr.ptr
config.decoding_method = "greedy_search".cstr.ptr
val recognizer = SherpaOnnxCreateOfflineRecognizer(config.ptr)
    ?: error("Could not load Whisper")
```

Create a stream with `SherpaOnnxCreateOfflineStream(recognizer)`, then pass the recording and read the result:

```kotlin
audio.samples.usePinned { pinned ->
    SherpaOnnxAcceptWaveformOffline(
        stream, audio.sampleRate,
        pinned.addressOf(0), audio.samples.size,
    )
}
SherpaOnnxDecodeOfflineStream(recognizer, stream)
currentCoroutineContext().ensureActive()
val result = SherpaOnnxGetOfflineStreamResult(stream)
    ?: error("No recognition result")
try {
    result.pointed.text?.toKString()?.trim().orEmpty()
} finally {
    SherpaOnnxDestroyOfflineRecognizerResult(result)
}
```

Wrap the stream and recognizer in `finally` blocks calling `SherpaOnnxDestroyOfflineStream` and `SherpaOnnxDestroyOfflineRecognizer`. Keep C strings within their `memScoped` lifetime and use pinned arrays only for the duration of the call.

For synthesis, zero a `SherpaOnnxOfflineTtsConfig` and fill Kitten’s paths:

```kotlin
config.model.kitten.model = modelPath.cstr.ptr
config.model.kitten.voices = voicesPath.cstr.ptr
config.model.kitten.tokens = tokensPath.cstr.ptr
config.model.kitten.lexicon = lexiconPath.cstr.ptr
config.model.kitten.length_scale = 1f
config.model.num_threads = 2
config.model.provider = "cpu".cstr.ptr
config.max_num_sentences = 1
config.silence_scale = 0.2f
```

Create the TTS handle with `SherpaOnnxCreateOfflineTts`. Zero a `SherpaOnnxGenerationConfig`, set the speaker ID and speed, then call `SherpaOnnxOfflineTtsGenerateWithConfig`. Copy the returned samples into a Kotlin `FloatArray` and retain their sample rate before destroying the generated audio and TTS handle. The [iOS engine](https://github.com/Debanshu777/Siren/blob/main/siren/src/iosMain/kotlin/io/github/debanshu/siren/speech/IosSpeechEngine.kt) shows the full allocation and cleanup sequence.

## 6. Run the models on desktop

Desktop uses sherpa’s separate [Java wrapper and native JARs](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1). Keep the native resources under `sherpa-onnx/native/<os-arch>/`; the upstream loader selects the platform, extracts the libraries and loads ONNX Runtime before JNI. The [JVM preparation task](https://github.com/Debanshu777/Siren/blob/main/buildSrc/src/main/kotlin/PrepareJvmRuntime.kt) packages these resources without model weights.

The Java API uses builders rather than Android’s Kotlin configuration classes:

```kotlin
val whisper = OfflineWhisperModelConfig.builder()
    .setEncoder(encoderPath).setDecoder(decoderPath)
    .setLanguage("en").setTask("transcribe").build()
val model = OfflineModelConfig.builder().setWhisper(whisper)
    .setTokens(tokensPath).setNumThreads(2).setProvider("cpu").build()
val recognizer = OfflineRecognizer(
    OfflineRecognizerConfig.builder().setOfflineModelConfig(model).build())
```

Create a stream, accept the PCM at its actual rate, decode and release the stream and recognizer as on Android. Kitten uses the Java TTS builders. The [complete JVM adapter](https://github.com/Debanshu777/Siren/blob/main/siren/src/jvmMain/kotlin/io/github/debanshu/siren/speech/JvmSpeechEngine.kt) also materializes classpath model resources into engine-owned temporary files and removes them on close. The sample uses JavaSound for capture and playback; its operating system handles microphone permission.

## 7. Connect the microphone and speaker

Android’s adapter records mono PCM16 with `AudioRecord`, converts shorts using `sample / 32768f`, and plays generated float PCM through `AudioTrack`. Stop and release capture before transcription; finish queued playback before releasing the output track.

On iOS, a small [AVFoundation bridge](https://github.com/Debanshu777/Siren/tree/main/sample/composeApp/src/iosMain/cinterop) captures the hardware format, mixes channels to mono, and preserves the rate. Kotlin copies and frees its returned buffer. Playback uses `AVAudioPlayer`.

Android needs `RECORD_AUDIO` in the manifest and a runtime permission request. iOS needs recording permission and this `Info.plist` entry:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Record a short clip for speech recognition on this device.</string>
```

## 8. Add a small Compose screen

The UI only needs a Record button, an editable transcript, and Speak. A shared [SpeechController](https://github.com/Debanshu777/Siren/blob/main/sample/composeApp/src/commonMain/kotlin/io/github/debanshu/siren/sample/SpeechController.kt) owns the current operation and exposes a `StateFlow<SpeechUiState>`.

Here’s the wiring using that controller. This is a smaller view of the same interaction shown in the GIF; the finished app adds the circular control and elapsed timer.

```kotlin
@Composable
fun SpeechScreen(platform: SpeechPlatform) {
    val controller = remember(platform) {
        SpeechController(platform,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.stop() }
    val state by controller.state.collectAsState()
    val idle = state.phase == Phase.Idle
    val recording = state.phase == Phase.Recording
    Column(Modifier.fillMaxSize().safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            enabled = (idle || recording) && state.capabilities.recognition,
            onClick = {
                if (recording) controller.transcribe() else controller.record()
            },
        ) { Text(if (recording) "Stop & transcribe" else "Record") }
        OutlinedTextField(
            value = state.text,
            onValueChange = controller::edit,
            enabled = idle,
            label = { Text("Transcript") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = controller::speak,
            enabled = idle && state.text.isNotBlank() && state.capabilities.synthesis) { Text("Speak") }
        if (!idle) {
            TextButton(onClick = controller::stop,
                enabled = state.phase != Phase.Stopping) { Text("Cancel") }
        }
        Text(state.phase.name)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
```

`record()` asks for permission and starts capture. The next tap calls `transcribe()`, which stops capture, passes its `PcmAudio` to the platform recognizer, and updates `state.text`. `edit()` changes that text. `speak()` passes it to `synthesize()`, then sends the returned audio to `play()`.

The disabled controls prevent overlapping actions. The controller stops recordings at 20 seconds and limits TTS input to 500 characters. It serializes operations and cleanup because cancelling a coroutine doesn’t instantly stop a native decode. `ON_STOP` stops audio when the app leaves the foreground; disposal closes the adapter.

Pass an `AndroidSpeechPlatform` from the Android activity or an `IosSpeechPlatform` from the iOS entry point. Both implement the contract above, so this Compose code stays in `commonMain`. See the [Android host](https://github.com/Debanshu777/Siren/blob/main/sample/androidApp/src/main/kotlin/io/github/debanshu/siren/sample/android/MainActivity.kt) for permission callback registration and the [iOS entry point](https://github.com/Debanshu777/Siren/blob/main/sample/composeApp/src/iosMain/kotlin/io/github/debanshu/siren/sample/MainViewController.kt) for its adapter creation.

## Try the complete flow

Record a sentence, transcribe it, change a few words, then tap Speak. Repeat with the network off. Also try denying microphone access and cancelling playback.

## Use Siren to skip the repeated wiring

The code above became **Siren**, a UI-free Kotlin Multiplatform SDK. It handles model configuration, native inference and cleanup. Its quick-start Gradle plugin also packages the default models, so your app only needs to handle the screen, microphone and speaker.

Both [Siren SDK 0.1.0](https://central.sonatype.com/artifact/io.github.debanshu777/siren/0.1.0) and the [model add-on 0.1.0](https://central.sonatype.com/artifact/io.github.debanshu777/siren-models-gradle-plugin/0.1.0) are on Maven Central. Run Gradle on JDK 21 or later. In `settings.gradle.kts`, keep `mavenCentral()` in both plugin and dependency repositories:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
```

Add this to your shared KMP module’s existing `plugins` block:

```kotlin
id("io.github.debanshu777.siren.models") version "0.1.0"
```

That adds the SDK dependency and both models. The first app build downloads the pinned Whisper tiny INT8 and Kitten nano INT8 files, verifies them and packages them as Android assets, JVM resources or iOS app resources through standard Xcode direct integration. No separate SDK dependency, manual file placement or model-loading call is needed. Android requires API 27 or later; this plugin’s Android integration uses the KMP Android library plugin.

The models add about 138 MB before app compression. They’re part of the installed app, so speech works offline from its first launch. The SDK itself still carries no model weights or runtime downloader. Custom iOS integration needs the resource-copy step described in the [quickstart](https://github.com/Debanshu777/Siren/blob/main/siren-models-gradle-plugin/README.md).

In an Android coroutine, use the recording captured earlier:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use

val replyAudio = createSpeechEngine(context).use { engine ->
    val text = engine.transcribe(recording.samples, recording.sampleRate)
    engine.synthesize(text)
}
```

On iOS or desktop, use `createSpeechEngine()` instead. `use` closes the engine after the operation, including failure or cancellation. Play `replyAudio.samples` at `replyAudio.sampleRate` through your app’s audio adapter. A screen that keeps an engine between actions should close it on disposal.

To supply your own models, turn off the default bundle:

```kotlin
sirenModels { bundleModels.set(false) }
```

The plugin still supplies the SDK, but downloads and packages no default models and skips the iOS copy. Provide your own bundled files or pass `SherpaModels.local(directory)` to the engine. Clean previous app build products once when switching. To manage the dependency yourself too, omit the plugin and add `implementation("io.github.debanshu777:siren:0.1.0")` to `commonMain.dependencies`.

The [configuration guide](https://github.com/Debanshu777/Siren/blob/main/docs/model-configuration.md) covers other compatible files and providers. The [sample](https://github.com/Debanshu777/Siren/tree/main/sample) shows the UI and audio adapters; they stay outside the SDK.

The recording → transcription → playback flow was checked on a Pixel 9. iOS was checked in the simulator; native inference also ran on macOS arm64 and Linux x64. Physical iPhone and desktop audio haven’t been checked. The [verification notes](https://github.com/Debanshu777/Siren/blob/main/docs/development.md) record those limits, and [third-party notices](https://github.com/Debanshu777/Siren/blob/main/THIRD_PARTY_NOTICES.md) cover the runtimes and models.
