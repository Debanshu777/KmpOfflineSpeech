# Configuring Siren's models

The quick path uses Siren's standard Whisper tiny INT8 + Kitten nano INT8 folder layout. Put the application-owned files under `models/`, then call `createSpeechEngine(context)` on Android or `createSpeechEngine()` on iOS/JVM. No individual path mapping is needed.

The SDK includes the layout helper and native inference runtime, **not model weights or a downloader**. Files are resolved when their model is used. See [getting started](getting-started.md) for the bundle tree and platform resource locations.

## Change the directory or capability

`SherpaModels.bundled()` is the default configuration. It selects both models under resource directory `models`, with two threads, English Whisper configuration and Kitten speaker zero. Override only what you need:

```kotlin
import io.github.debanshu.siren.speech.SherpaModels

val anotherResourceFolder = SherpaModels.bundled(directory = "speech-models")
val recognitionOnly = SherpaModels.bundled(synthesis = false)
val synthesisOnly = SherpaModels.bundled(recognition = false)
val localFiles = SherpaModels.local("/absolute/path/to/models")
```

Both helpers expect `whisper/` and `kitten/` subfolders with the known seven filenames. They return descriptors without downloading or opening files. A local directory must resolve to absolute readable files on the target platform. Bundled paths are portable relative resource paths; an empty directory selects the resource root. Traversal and absolute bundled paths are rejected.

Pass an override to the same factory. In Android code:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine

val engine = createSpeechEngine(context, models = synthesisOnly)
```

In iOS/JVM code:

```kotlin
import io.github.debanshu.siren.speech.createSpeechEngine

val engine = createSpeechEngine(models = synthesisOnly)
```

Only configured providers are constructed. `SpeechEngine.capabilities` reports that configuration and its labels, not a file-readiness check; the sample disables absent controls. `SherpaModels()` itself configures neither capability, whereas the factory's default is `SherpaModels.bundled()`.

These are runtime choices. They do not trim native binaries or model assets already packaged by a build. Default sample preparation still obtains both demonstration bundles. The SDK artifact contains no weights.

## Resource and file ownership

| Descriptor | Android | iOS | JVM |
| --- | --- | --- | --- |
| `ModelFile.Bundled` | Relative Android asset path; materialized into app-private files | Relative application resource path | Relative classpath resource path; materialized into engine-owned temporary files |
| `ModelFile.Local` | Absolute readable filesystem path | Absolute readable app-accessible path | Absolute readable filesystem path |

JVM `createSpeechEngine` accepts an optional `ClassLoader`; by default it uses the thread context loader, falling back to the system loader. Engine close removes its temporary resource copies, not caller-supplied Local files. Applications own verified acquisition, updates, storage and applicable model licenses.

Use the suspending `engine.use { ... }` helper for an operation scope that should close automatically. For a long-lived host/controller, keep one engine owner and close it when that owner is disposed. Do not put an engine in `use` and then reuse it after the block.

## Advanced: custom compatible exports

Use typed descriptors when filenames or model settings differ from the standard bundle:

```kotlin
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel

val custom = SherpaModels(
    recognition = WhisperModel(
        encoder = ModelFile.Local("$modelDirectory/encoder.int8.onnx"),
        decoder = ModelFile.Local("$modelDirectory/decoder.int8.onnx"),
        tokens = ModelFile.Local("$modelDirectory/tokens.txt"),
        language = "en",
        displayName = "My Whisper export",
    ),
    synthesis = null,
    threads = 2,
)
```

`modelDirectory` is an absolute directory supplied by the application. Encoder, decoder and tokens must belong to one compatible sherpa Whisper export. `KittenModel` similarly configures its model, voices, tokens, lexicon, speaker ID, speed and display label. An arbitrary ONNX graph does not become compatible by changing a filename.

The original named factories `createAndroidSpeechEngine`, `createIosSpeechEngine` and `createJvmSpeechEngine` remain available and also default to `SherpaModels.bundled()`. They provide the same inference capabilities; basic integrations can use the shorter platform `createSpeechEngine` name.

## Advanced: another model family

Implement `SpeechRecognizer`, `SpeechSynthesizer`, or both, and compose them:

```kotlin
fun recognitionOnly(provider: SpeechRecognizer): SpeechEngine =
    ModelSpeechEngine(recognizer = provider)
```

Providers expose labels, move blocking inference off Main, return independent mono PCM at its real sample rate, and own native cleanup. Keep configuration specific to the model family. No UI, hardware audio or generic camera/vision framework is part of the SDK.

`ModelSpeechEngine` serializes calls and teardown. Cancellation can discard obsolete results but cannot interrupt synchronous native decoding instantly. Built-in Whisper adapters accept 0.2–20-second clips. The sample adds its own 20-second recording and 500-character TTS limits.

The sample audio hosts accept any `SpeechEngine`; they own microphone permission, capture/playback and engine disposal. `SampleModels.bundledDemo()` remains a sample wrapper around the SDK's resource-layout helper, not a required SDK dependency.

## Release status

`io.github.debanshu777:siren:0.1.0` is available from Maven Central. Convenience defaults add no weights, hidden downloads or UI. See [release verification](releasing.md) for public delivery, consumer checks and native audit status.

## Recent Changes

Added bundled/local layout helpers and default factory configuration, with scoped automatic cleanup and custom descriptors/SPI retained for advanced integrations.
