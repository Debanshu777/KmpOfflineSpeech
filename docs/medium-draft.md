# Run Speech-to-Text and Text-to-Speech Models in Kotlin Multiplatform

Integrate Whisper and KittenTTS into an existing Android and iOS app, with local inference and a shared Kotlin interface.

A speech feature has a straightforward shape: record audio, turn it into text, let someone edit it, then turn that text back into audio. The interesting work is obtaining the right model files, loading them through native runtimes, and moving samples across the platform boundary correctly.

This tutorial starts with an existing Kotlin Multiplatform project targeting Android and iOS. We will build the speech integration from scratch. The examples use `composeApp` for shared code and `androidApp` for the Android host; adjust module names to your project. The [finished example](https://github.com/Debanshu777/KmpOfflineSpeech) supplies complete files alongside the excerpts below.

![Record, transcribe and play in the actual Android app](images/speech-demo.gif)

*Actual Android app demonstration. The verification stimulus is synthesized speech played through the speaker and physically recorded through the microphone; this is not a human speech-accuracy benchmark.*

## 1. Obtain the models and their supporting files

We will use **Whisper tiny multilingual INT8 ONNX** for recognition, configured for English in this demo, and **KittenTTS Nano v0.8 INT8** for English synthesis. **sherpa-onnx** provides the speech APIs and runs the models using ONNX Runtime. These are packaged models, rather than calls to system speech services or a server.

Weights and runtimes are different dependencies. An ONNX model contains learned data; the runtime contains executable inference code. Supporting files describe tokens, voices, and text pronunciation. Keep each bundle together:

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

Download the three Whisper files from the [pinned export revision](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c). For Kitten, extract the model, voices and tokens from the [Nano v0.8 INT8 archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2), then add the separate [English lexicon](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt). A Kitten ONNX file without its voice data and lexicon is incomplete.

Record download URLs, expected sizes and SHA-256 checksums in a dependency manifest. For example, the encoder entry in this project's `scripts/dependencies.json` is:

```json
{
  "name": "tiny-encoder.int8.onnx",
  "url": "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-encoder.int8.onnx?download=true",
  "sha256": "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
  "bytes": 12937772,
  "destination": "models/whisper/tiny-encoder.int8.onnx"
}
```

The [complete manifest](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/scripts/dependencies.json) includes both model bundles and the native binaries. Pin files rather than following a mutable branch or silently accepting a changed release asset.

## 2. Prepare native runtimes through Gradle

For Android, use the official [sherpa-onnx 2.0.1 AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar). It contains Kotlin-facing APIs and native libraries. For iOS, use the static sherpa C XCFramework and the matching ONNX Runtime framework from upstream's [tagged package manifest](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/Package.swift).

There is a small naming trap: the referenced ONNX Runtime package is `1.28.2`, but its [manifest points to 1.28.1 binary archives](https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/Package.swift). Follow the actual URLs and checksums.

Make preparation a Gradle responsibility. A typed task should declare its manifest input, download cache, and generated outputs. Download to a temporary file, verify its size and streaming SHA-256, then publish it. Validate cached downloads too. For archives, reject traversal, links, duplicate paths and excessive expansion.

The example's `buildSrc` tasks are `PrepareSpeechModels`, `PrepareAndroidSpeech`, `PrepareIosSpeech`, and `PrepareIosAudio`. Its [build tools guide](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/buildSrc/README.md) explains the implementation. Model preparation is registered like this:

```kotlin
val prepareSpeechModels = tasks.register<PrepareSpeechModels>("prepareSpeechModels") {
    manifestFile.set(layout.projectDirectory.file("scripts/dependencies.json"))
    downloadCache.set(layout.projectDirectory.dir(".cache/downloads"))
    offline.set(gradle.startParameter.isOffline)
    modelDirectory.set(layout.projectDirectory.dir("models"))
}
```

Android preparation takes this output, copies the AAR into `composeApp/libs`, and creates `composeApp/build/generated/speechAssets/models`. In the shared module, connect the generated AAR to `androidMain`:

```kotlin
val prepareAndroidSpeech = rootProject.tasks
    .named<PrepareAndroidSpeech>("prepareAndroidSpeech")

// Inside androidMain.dependencies:
implementation(files(prepareAndroidSpeech.flatMap { it.runtimeAar })
    .builtBy(prepareAndroidSpeech))
```

Register its asset directory through Android Components:

```kotlin
extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareAndroidSpeech, PrepareAndroidSpeech::assetsDirectory)
    }
}
```

The task provider carries the dependency, so assembly prepares its inputs. iOS preparation extracts frameworks into `libs/ios` and leaves the models ready for bundling as a folder resource. Ignore weights, runtimes and download caches in Git. Keep downloads inside task actions, rather than project configuration.

## 3. Pass audio through a small shared contract

Create a shared audio value in `commonMain`:

```kotlin
data class PcmAudio(val samples: FloatArray, val sampleRate: Int)
```

Use mono float PCM, normally between `-1f` and `1f`. Validate a positive rate and nonempty samples. The core platform operations are `suspend fun transcribe(audio: PcmAudio): String` and `suspend fun synthesize(text: String): PcmAudio`. Capture and playback can sit behind the same platform interface.

Keep the real sample rate with every recording. Relabelling 48 kHz input as 16 kHz changes its timing; it does not resample it. The recognizer API accepts the original rate and sherpa performs conversion internally, as shown in its [offline stream implementation](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/csrc/offline-stream.cc).

## 4. Load Whisper and Kitten on Android

In `androidMain`, import `com.k2fsa.sherpa.onnx` classes from the AAR. Before initialization, copy packaged model assets to `context.filesDir/models` on `Dispatchers.IO`, because this configuration uses filesystem paths. Copy through a temporary file, close streams, and publish only after the copy completes.

Create the recognizer on `Dispatchers.Default`, away from the UI thread:

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

A recognizer owns the model session; a stream holds one recording's input and result:

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

For TTS, configure all four Kitten files:

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

Speaker zero is selected on both platforms. Use the returned output rate for playback. The sample creates a session per operation to keep ownership visible; caching sessions would reduce repeated loading work but requires the same serialization and cleanup rules. The [Android adapter](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/androidMain/kotlin/dev/debanshu/speech/AndroidSpeechPlatform.kt) includes the complete error paths.

## 5. Bind and link the iOS C API

On iOS, call sherpa's C API directly from Kotlin/Native. In `iosMain/cinterop`, create a header including `sherpa-onnx/c-api/c-api.h` and a definition file:

```properties
headers = speech_audio.h
package = dev.debanshu.speech.native
```

Our header also declares audio-bridge functions; model inference itself needs no custom C++ wrapper. In the shared Gradle build, select the correct XCFramework slice for each target:

```kotlin
val slice = if (target.name == "iosArm64")
    "ios-arm64" else "ios-arm64_x86_64-simulator"
val sherpa = rootProject.file("libs/ios/SherpaOnnxC.xcframework/$slice")
val ort = rootProject.file("libs/ios/onnxruntime.xcframework/$slice")
val interop = target.compilations.getByName("main")
    .cinterops.create("speechNative") {
        defFile("src/iosMain/cinterop/speech.def")
        compilerOpts("-I${file("src/iosMain/cinterop")}",
            "-I$sherpa/SherpaOnnxC.framework/Headers")
    }
```

Add framework search paths and link `SherpaOnnxC`, `onnxruntime`, C++, the audio bridge, AVFoundation, AudioToolbox, Foundation, CoreML and CoreFoundation. Generating bindings and linking are separate: cinterop creates Kotlin declarations; the linker supplies their implementations.

Make cinterop and framework linking depend on the appropriate preparation task. The example uses `prepareIosAudioIosArm64` and `prepareIosAudioIosSimulatorArm64`, which prepare the runtimes and compile its audio bridge before the Kotlin framework needs them.

## 6. Initialize and run the models on iOS

Opt into `ExperimentalForeignApi`. Allocate the C config in `memScoped`, zero it, then fill the model paths and execution options:

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

Create a stream, pass the recording, decode, and copy the result:

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

The surrounding `finally` blocks destroy the stream and recognizer. `usePinned` stabilizes the Kotlin array's address for the call; do not retain its pointer afterward. Temporary C strings stay inside the `memScoped` lifetime.

Kitten uses a separate zero-initialized config:

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

Here `config` is a `SherpaOnnxOfflineTtsConfig`. Create it with `SherpaOnnxCreateOfflineTts`, then zero a `SherpaOnnxGenerationConfig`, set `sid = 0` and `speed = 1f`, and call `SherpaOnnxOfflineTtsGenerateWithConfig`.

Copy `output.pointed.samples` into a Kotlin `FloatArray` and preserve `output.pointed.sample_rate`. Destroy the generated audio only after copying it, then destroy the TTS handle. A native pointer is not an independently owned Kotlin buffer. The [iOS adapter](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/composeApp/src/iosMain/kotlin/dev/debanshu/speech/IosSpeechPlatform.kt) shows the nested cleanup; [Kotlin's C interop guide](https://kotlinlang.org/docs/native-c-interop.html) explains pointer scopes and callback ownership.

Resolve iOS model paths from `NSBundle.mainBundle.resourcePath`, for example `models/kitten/model.int8.onnx`, and check that each file exists before creating a session. An asset copied into the wrong bundle location can look like a model-loading failure. Use the exact exported bundles here: an arbitrary ONNX speech model will not necessarily match Whisper's encoder/decoder contract or Kitten's input configuration. Start by loading and running each model independently, then connect them through the shared audio value. This makes missing files, incompatible exports and audio-format problems easier to distinguish.

## 7. Connect microphone input, playback and permissions

Android uses mono PCM16 `AudioRecord`, trying 16 kHz with 48 kHz and 44.1 kHz fallbacks. Check initialization and read errors. Convert shorts with `sample / 32768f`. Stop capture, join the reader and release the recorder before transcribing. Android's [AudioRecord reference](https://developer.android.com/reference/android/media/AudioRecord) documents supported-format constraints.

For output, stream float PCM to `AudioTrack` at the TTS result's rate. Handle partial writes and wait for the playback head to consume queued frames before releasing the track.

On iOS, an AVAudioEngine input tap captures the hardware format, averages channels to mono and preserves the actual rate. The small Objective-C++ bridge returns an owned buffer that Kotlin copies and frees. Its player packages generated samples for AVAudioPlayer. Audio sessions and handles stay on Main; model work runs on Default.

Both capture implementations bound storage to 20 seconds. Add Android's `RECORD_AUDIO` permission to the manifest and request it at runtime. On iOS, request recording permission and include:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>Record a short clip for speech recognition on this device.</string>
```

Apple [requires the purpose string](https://developer.apple.com/documentation/bundleresources/information-property-list/nsmicrophoneusagedescription). For this Compose host, also retain `CADisableMinimumFrameDurationOnPhone = true`. Add `models` to the iOS app as a folder resource so paths such as `models/whisper/tiny-tokens.txt` survive bundling.

## 8. Own operations, then verify the whole path

Expose recording, transcription, synthesis and playback states to your shared UI. Keep one operation owner, serialize model calls and teardown with a mutex, and show errors without disabling unrelated capabilities. The sample limits synthesis to 500 characters and rejects recordings shorter than 0.2 seconds.

Cancellation cannot instantly interrupt a synchronous native decode. Check coroutine cancellation after decoding, discard obsolete results, and wait for model ownership before destroying handles. Stop capture/playback when the app leaves the foreground, and close the adapter when its screen is disposed. Background recording, streaming transcripts and automatic route recovery are outside this small example.

In the companion project, Android assembly prepares its generated assets and runtime automatically:

```bash
./gradlew :androidApp:assembleDebug
```

For its iOS host:

```bash
./gradlew prepareIosSpeech prepareIosAudioIosSimulatorArm64
open iosApp/iosApp.xcodeproj
```

The pinned host uses iOS 18.5+ because its Compose toolchain includes an ICU dependency requiring that minimum; this is not a general minimum for speech inference. Android's sample minimum is API 26. Build and test your own project's selected targets and toolchain.

Run shared ownership/cancellation tests and preparation regressions:

```bash
./gradlew :composeApp:jvmTest checkSpeechSetup
```

Then test the complete interaction on each target: record a short sentence, transcribe, edit, and speak. Try permission denial, cancellation, the timeout, and leaving the screen. Disable Wi-Fi and cellular connectivity, relaunch the installed app, and repeat. Downloads happen during preparation; inference uses packaged assets.

The preparation regressions, Android build and iOS simulator host build passed. On a Pixel 9 running Android 17, Kitten generated speech, AudioTrack played it, AudioRecord captured the speaker through the microphone, and Whisper returned “The quick round fox jumps over the lazy dog.” The stimulus used “brown,” so recognition was not word-perfect. The [acoustic evidence](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/androidApp/verification/acoustic-roundtrip-2026-11-21.json) preserves both runs and their acceptance criteria. A separate [iOS native roundtrip](https://github.com/Debanshu777/KmpOfflineSpeech/blob/main/docs/ios-native-smoke.json) checks the simulator's model pair, rather than an iPhone microphone or speaker. These are integration checks, not accuracy or latency benchmarks.

Keep licenses with the integration. sherpa-onnx uses [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/LICENSE), Whisper uses [MIT](https://github.com/openai/whisper/blob/main/LICENSE), and the selected [Kitten Nano INT8 model](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8) identifies Apache-2.0. Preserve notices attached to runtime binaries and supporting assets as well.

The resulting boundary is small: shared Kotlin owns the interaction and audio values; each platform loads the models and supplies audio hardware access. That is enough to add local speech to an existing KMP application without adopting a larger application's infrastructure.
