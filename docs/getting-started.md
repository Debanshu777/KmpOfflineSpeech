# Add speech with Siren

The basic integration is: add Siren, place the known model bundle in your app, then create an engine and call it. Individual model-path mapping and manual `finally` cleanup are unnecessary for this layout.

**Release status:** `io.github.debanshu777:siren:0.1.0` remains prepared locally and unpublished. The Central dependency below is intended for a future release. Use the source/local staging meanwhile; [release preparation](releasing.md) records current status.

## Add the dependency

In your existing KMP project, use `mavenCentral()` and add Siren to `commonMain`:

```kotlin
commonMain.dependencies {
    implementation("io.github.debanshu777:siren:0.1.0")
}
```

KMP selects the platform runtime artifact. You do not copy SDK native libraries or configure checkout-local native linker paths.

## Place the model bundle

The default `createSpeechEngine` configuration knows this layout:

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

Use the three files from the [pinned Whisper tiny export](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c), extract the model/voices/tokens from the [Kitten nano INT8 bundle](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2), and add the separate [English lexicon](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt). The [manifest](../scripts/dependencies.json) records hashes/sizes for verified acquisition. The source sample can prepare this tree with `./gradlew prepareSpeechModels`.

Package the tree in your application:

| Platform | Bundle location |
| --- | --- |
| Android | Android assets, for example host `src/main/assets/models/` |
| iOS | Add `models` as a folder resource, preserving subdirectories |
| JVM | Classpath resources, for example `src/jvmMain/resources/models/` in a KMP host |

Siren includes native inference code and the file-layout preset, **not these model weights**. Creation performs no network downloads. Files/native sessions open when the corresponding operation runs. Applications own verified installation and applicable model licenses.

## Create and use the engine

Call the factory from the relevant platform source set, inside a coroutine.

Android (`androidMain`):

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.use

val audio = createSpeechEngine(context).use { engine ->
    engine.synthesize("Hello from Kotlin Multiplatform.")
}
```

`context` is supplied by your Android host; no global context or initialization singleton is needed.

iOS or desktop (`iosMain` / `jvmMain`):

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.use

val audio = createSpeechEngine().use { engine ->
    engine.synthesize("Hello from Kotlin Multiplatform.")
}
```

The returned `PcmAudio` contains mono float `samples` and `sampleRate`. Send it to your application's playback adapter at that rate. The samples remain valid after the engine scope closes.

## Transcribe your recorded audio

The `transcribe(samples, sampleRate)` overload accepts a recorded mono `FloatArray` and its actual rate. For a recording-to-text-to-speech operation in an Android coroutine:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine
import io.github.debanshu.siren.speech.transcribe
import io.github.debanshu.siren.speech.use

val replyAudio = createSpeechEngine(context).use { engine ->
    val text = engine.transcribe(recordedSamples, recordedSampleRate)
    engine.synthesize(text)
}
```

On iOS/JVM, use `createSpeechEngine()` instead; the block stays the same. You can also call `transcribe(PcmAudio(...))` when your application already carries that value.

Samples normally range from `-1f` to `1f`. Do not mutate an input array while inference reads it. Preserve the hardware recording rate; relabelling 48 kHz samples as 16 kHz is not resampling. The recognizer handles its model-rate conversion. Playback uses the generated rate, which can differ from the input rate.

The SDK does not request microphone permission, record or play audio. Existing application audio code can supply/consume PCM; [sample adapters](../sample/composeApp/README.md) illustrate AudioRecord/AudioTrack, AVFoundation and JavaSound.

## Local files or one capability

If the same tree is already installed on disk, provide its root once:

```kotlin
import io.github.debanshu.siren.speech.SherpaModels

val models = SherpaModels.local("/absolute/path/to/models")
```

Pass `models` to `createSpeechEngine(context, models)` on Android or `createSpeechEngine(models)` on iOS/JVM. To omit a capability, use `SherpaModels.bundled(recognition = false)` or `SherpaModels.bundled(synthesis = false)`. A custom resource folder is `SherpaModels.bundled(directory = "speech-models")`.

These helpers only describe the known Whisper/Kitten layout. They do not download, copy or load models themselves, and disabling a capability does not remove files already packaged by the application. See [configuration](model-configuration.md) for custom filenames, settings and other model families.

## Lifecycle and support

`use` closes the engine automatically after success, failure or cancellation. It performs cleanup in a non-cancellable context and preserves an operation failure if cleanup also fails. Do not reuse the engine after the scope.

For a screen that performs many separate operations, keep a lifecycle-owned engine and call `close()` when that owner finishes. An engine serializes operations and teardown. Cancellation cannot instantly interrupt synchronous native decoding; cleanup waits for it to return.

Built-in Whisper accepts 0.2–20-second recordings. The sample's 500-character TTS limit is application policy. Streaming and background/audio-route behavior require application-specific work.

Android's floor is API 27. The reference build uses Kotlin 2.4.21 and Java 17 bytecode; contributors use JDK 21. The pinned sample uses iOS 18.5 arm64 targets. Native JVM inference is verified on macOS arm64 and Linux x64; four other desktop payloads are packaged but execution-untested. See [evidence](development.md).

## Recent Changes

Reduced the basic path to a model bundle, one-line engine creation and scoped calls. Bundled/local helpers, PCM transcription and automatic cleanup preserve custom APIs for advanced cases.

Android minimum SDK is 27 throughout the library, samples and isolated consumer. The pinned ONNX Runtime Android build uses `--android_api 27`, corroborated by the bundled ELF Android API notes; declaring 26 would understate that build floor.
