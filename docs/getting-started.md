# Add Siren to an existing KMP application

Siren supplies model inference on Android, iOS and JVM. It does not supply UI, Compose components, microphone permission handling, recording, playback or model weights. Begin with an existing KMP application and choose which speech capabilities it needs.

**Release status:** `io.github.debanshu777:siren:0.1.0` is the intended first-release coordinate. It is prepared locally, not published; the Central dependency shown below becomes usable only after a future release. For now, use the source module or a local staged Maven repository.

The [release preparation guide](releasing.md) distinguishes local candidate checks from the deferred public release.

## Install the SDK

Use Maven Central in your existing repository configuration:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Add the dependency to the shared source set:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.debanshu777:siren:0.1.0")
        }
    }
}
```

KMP selects the platform artifact. Android carries wrapper classes/JNI; JVM carries wrapper classes/native resources; iOS carries target-specific static archives in cinterop KLIBs. Consumers do not copy the SDK checkout's native libraries or configure its local `-F` paths.

The reference build uses Kotlin 2.4.21, Java 17 bytecode and Android API 26+. The sample's pinned Compose toolchain uses iOS 18.5 on arm64 devices/simulators. JVM native inference is tested on macOS arm64 and Linux x64; four other packaged desktop classifiers remain execution-untested. See [current support/evidence](development.md).

## Obtain compatible model files

For the demonstration bundles, use the three files from the [pinned Whisper tiny export](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c), the [Kitten nano INT8 archive](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2), and the separate [English lexicon](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt).

```text
models/
  whisper/tiny-encoder.int8.onnx
  whisper/tiny-decoder.int8.onnx
  whisper/tiny-tokens.txt
  kitten/model.int8.onnx
  kitten/voices.bin
  kitten/tokens.txt
  kitten/lexicon-en-us.txt
```

The [artifact manifest](../scripts/dependencies.json) records pinned URLs, sizes and SHA-256 checksums. Verify downloaded content before installation, and retain applicable model/support-file notices. A model filename alone does not establish compatibility with the Whisper encoder/decoder or Kitten voice/token contract.

You can install weights in app-private storage and use `ModelFile.Local`, or package your own resources and use `ModelFile.Bundled`:

| Descriptor | Android | iOS | JVM |
| --- | --- | --- | --- |
| `Local` | Absolute readable file path | Absolute readable file path in the app-accessible filesystem | Absolute readable file path |
| `Bundled` | Path relative to Android assets | Path relative to application resources | Classpath resource path |

For example, Android host assets can live under `src/main/assets/models/`. Add the iOS `models` folder as a folder resource to preserve nested paths. JVM resources can live under `src/jvmMain/resources/models/` in a KMP host or `src/main/resources/models/` in a JVM host. These are application assets, not SDK files.

The source sample's `prepareSpeechModels` Gradle task is an optional reference for verified preparation; a Maven consumer does not need that task or the sample module.

## Configure both capabilities explicitly

The following shared function accepts an absolute model-root directory with the tree above:

```kotlin
import io.github.debanshu.siren.speech.KittenModel
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel

fun localModels(root: String): SherpaModels = SherpaModels(
    recognition = WhisperModel(
        encoder = ModelFile.Local("$root/whisper/tiny-encoder.int8.onnx"),
        decoder = ModelFile.Local("$root/whisper/tiny-decoder.int8.onnx"),
        tokens = ModelFile.Local("$root/whisper/tiny-tokens.txt"),
        language = "en",
    ),
    synthesis = KittenModel(
        model = ModelFile.Local("$root/kitten/model.int8.onnx"),
        voices = ModelFile.Local("$root/kitten/voices.bin"),
        tokens = ModelFile.Local("$root/kitten/tokens.txt"),
        lexicon = ModelFile.Local("$root/kitten/lexicon-en-us.txt"),
        speakerId = 0,
        speed = 1f,
    ),
    threads = 2,
)
```

For packaged assets replace each `Local` with its `Bundled("models/...")` path. Android materializes bundled files in app-private storage. JVM copies classpath model resources into engine-owned temporary files and removes them on close; caller-supplied Local files remain caller-owned.

Use `models.copy(synthesis = null)` for recognition only or `models.copy(recognition = null)` for synthesis only. `SherpaModels()` enables neither. These runtime choices do not trim native libraries or model assets already included by a build. No model preset is built into the SDK; `SampleModels.bundledDemo()` belongs only to the separate sample.

## Create the platform engine

Each factory requires explicit model configuration. Call the appropriate function from that target's source set:

```kotlin
// androidMain
import android.content.Context
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createAndroidSpeechEngine

fun androidEngine(context: Context, modelRoot: String): SpeechEngine =
    createAndroidSpeechEngine(context.applicationContext, localModels(modelRoot))
```

```kotlin
// iosMain
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createIosSpeechEngine

fun iosEngine(modelRoot: String): SpeechEngine =
    createIosSpeechEngine(localModels(modelRoot))
```

```kotlin
// jvmMain
import io.github.debanshu.siren.speech.SpeechEngine
import io.github.debanshu.siren.speech.createJvmSpeechEngine

fun jvmEngine(modelRoot: String): SpeechEngine =
    createJvmSpeechEngine(localModels(modelRoot))
```

The JVM factory optionally accepts a `ClassLoader` for bundled models; its default uses the thread context loader, falling back to the system loader. Native sessions open lazily when an inference operation runs. Factories do not download models or request microphone access.

## Run recognition and synthesis

The shared audio boundary is caller-owned mono float PCM plus its actual sample rate:

```kotlin
import io.github.debanshu.siren.audio.PcmAudio
import io.github.debanshu.siren.speech.SpeechEngine

suspend fun transcribeAndSynthesize(
    engine: SpeechEngine,
    recordedSamples: FloatArray,
    recordedSampleRate: Int,
): PcmAudio {
    val input = PcmAudio(recordedSamples, recordedSampleRate)
    val text = engine.transcribe(input)
    return engine.synthesize(text)
}
```

Samples normally range from `-1f` to `1f`. Do not mutate an input array while inference consumes it. Preserve the microphone's actual rate; changing the rate label is not resampling. The built-in recognizer converts supported input for Whisper internally. Use the returned PCM's rate for playback, even when it differs from the recording rate.

Capture/playback adapters belong to your application. The [sample audio hosts](../sample/composeApp/README.md) illustrate AudioRecord/AudioTrack, AVFoundation and JavaSound without adding those concerns to the SDK.

Keep one owner for each engine and call its suspending `close()` when that owner finishes. `ModelSpeechEngine` serializes inference and close. Cancellation can suppress a result but cannot forcibly interrupt a synchronous native decode; cleanup waits until native work returns. Returned PCM is independently owned, so it remains valid after engine close. Clear or release audio buffers when your application no longer needs them.

Built-in Whisper adapters accept 0.2–20-second clips. The sample's 500-character TTS limit is an application policy rather than a universal SDK limit. Background capture, streaming and route recovery need application-specific work.

## Supply another model family

Implement `SpeechRecognizer`, `SpeechSynthesizer`, or both, and compose them with `ModelSpeechEngine`. Providers have display labels, own native cleanup and must return independent PCM buffers at their real sample rate. Use concrete model-specific configuration instead of passing an arbitrary ONNX graph into the Whisper/Kitten descriptors.

See [configuration](model-configuration.md) for SPI examples, [architecture](architecture.md) for ownership, and [development](development.md) for real verification and release status.

## Recent Changes

Added the first-release installation guide with explicit Android/iOS/JVM factories, complete model bundles, shared PCM operations and application-owned lifecycle/audio.
