# Configuring Siren's speech models

The [`:siren` SDK](../siren/README.md) separates inference from the sample's microphone, playback and Compose UI. Built-in providers support compatible sherpa Whisper and Kitten exports. Other model families need concrete adapters; an arbitrary ONNX filename does not establish compatible inputs and outputs.

## Select either capability

Native factories require an explicit `SherpaModels`. The demonstration preset lives only in `:sample:composeApp`:

```kotlin
import io.github.debanshu.siren.sample.SampleModels
import io.github.debanshu.siren.speech.SherpaModels

val preset = SampleModels.bundledDemo()
val recognitionOnly = preset.copy(synthesis = null)
val synthesisOnly = preset.copy(recognition = null)
val noModels = SherpaModels()
```

SDK consumers configure files themselves; they do not need the sample module or preset. Only configured providers are constructed. Files and native sessions open lazily when an operation runs. Absent capabilities are reflected in `SpeechEngine.capabilities`; the sample disables their controls and avoids microphone access when recognition is absent.

Runtime choices do not change packaged contents. Default sample Gradle preparation still obtains both demonstration bundles. Setting a model to null does not slim its APK. SDK artifacts contain runtime libraries and notices, but no model weights; applications own weight acquisition and packaging.

## Supply compatible files

Use `ModelFile.Local` for absolute filesystem paths, or `ModelFile.Bundled` for paths relative to Android assets, iOS application resources or JVM classpath resources. Native adapters check files before model creation. Bundled paths reject traversal and are not download URLs.

```kotlin
import io.github.debanshu.siren.speech.ModelFile
import io.github.debanshu.siren.speech.SherpaModels
import io.github.debanshu.siren.speech.WhisperModel

val models = SherpaModels(
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

`modelDirectory` is an absolute directory supplied by the application. Encoder, decoder and tokens must belong to one compatible Whisper export. `KittenModel` names its model, voices, tokens and lexicon and configures `speakerId`, `speed` and `displayName`.

Android SDK usage:

```kotlin
import io.github.debanshu.siren.speech.createAndroidSpeechEngine
val engine = createAndroidSpeechEngine(applicationContext, models)
```

iOS SDK usage:

```kotlin
import io.github.debanshu.siren.speech.createIosSpeechEngine
val engine = createIosSpeechEngine(models)
```

JVM/Desktop SDK usage:

```kotlin
import io.github.debanshu.siren.speech.createJvmSpeechEngine
val engine = createJvmSpeechEngine(models)
```

The JVM factory optionally accepts a `ClassLoader`, defaulting to the current thread's context loader or the system loader. Bundled weights are classpath resources materialized into engine-owned temporary files; close removes those copies. Local paths remain caller-owned.

Factories have no implicit demonstration configuration and do not download weights. Android copies bundled assets to app-private files for native filesystem access. iOS resolves bundled paths from application resources. Local files must be absolute, readable and nonempty. Applications remain responsible for verified acquisition, updates, storage and licenses.

## Connect the sample audio host

The sample, rather than the SDK, adds permissions and hardware audio:

```kotlin
// Android, in Activity.onCreate before STARTED:
val platform = createAndroidSpeechPlatform(this, engine)

// iOS:
val platform = IosSpeechPlatform(engine)

// JVM/Desktop:
val platform = JvmSpeechPlatform(engine)
```

Audio hosts are in `io.github.debanshu.siren.sample.platform`. Android registers its permission callback before STARTED. JVM uses JavaSound with OS-level microphone access checked when the input opens; it has no portable permission preflight. The sample controller owns and closes its platform/engine on disposal; do not share that engine between independently owned hosts. A headless SDK consumer needs neither audio host nor Compose.

## Add another model family

Implement `SpeechRecognizer`, `SpeechSynthesizer`, or both with `audio.PcmAudio` as the boundary:

```kotlin
fun recognitionOnly(provider: SpeechRecognizer): SpeechEngine =
    ModelSpeechEngine(recognizer = provider)
```

Providers expose display labels, move blocking inference off Main, preserve real audio sample rates, return independently owned buffers and pair native allocations with cleanup. Model-specific parameters belong to their adapters. No camera/vision APIs or generic model registry are included.

`ModelSpeechEngine` serializes operations and close, preserves cancellation, and discards obsolete synthesized audio. Synchronous native decoding may finish before cancellation cleanup can complete. Close an engine before replacing its immutable provider configuration.

The built-in Whisper adapters accept 0.2–20-second clips. The sample additionally caps recording at 20 seconds and TTS input at 500 characters. New adapters should document their own input and duration requirements.

## Distribution status

Public contracts and native providers live in `:siren`, with explicit API mode and no UI dependency. Local development artifacts are staged as `io.github.debanshu777:siren:0.1.0-SNAPSHOT` under `build/staging-repository`; no public release is available. See [distribution gates](library-design.md) for independent consumer and compatibility requirements.

## Recent Changes

Added explicit JVM factory/class-loader configuration and engine-owned resource materialization alongside Android/iOS, with sample-only presets and independent capabilities.
