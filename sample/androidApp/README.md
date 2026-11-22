# Siren Android sample host

`:sample:androidApp` launches the shared sample and contains instrumentation tests. It depends on `:sample:composeApp`, which consumes the UI-free `:siren` SDK. Runtime JNI/wrapper code comes from the SDK; demonstration weights come from the sample's generated assets. The manifest requests microphone access and no Internet permission.

The launcher/tests use `io.github.debanshu.siren.sample.android`. The installed application ID remains `dev.debanshu.speech.sample`. It registers the audio permission launcher before STARTED and constructs an explicit engine with `createAndroidSpeechEngine(applicationContext, SampleModels.bundledDemo())`, then passes it to `createAndroidSpeechPlatform(activity, engine)`. The sample descriptor delegates to `SherpaModels.bundled()`. Apps packaging that layout can call `createSpeechEngine(context)` directly; weights, microphone access and UI remain application responsibilities.

Choose other bundled/local files through `SherpaModels`, omit a capability, or inject `ModelSpeechEngine` with custom providers. See [configuration](../../docs/model-configuration.md). Audio and UI use capabilities and labels rather than assuming a model family.

## Build and native checks

From the repository root:

```bash
./gradlew :sample:androidApp:assembleDebug
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:connectedDebugAndroidTest
```

The sample supports API 26+, arm64-v8a and x86_64. Assembly prepares SDK runtime inputs independently from sample model assets. The SDK embeds the pinned wrapper JAR, selected JNI libraries, notices and consumer rules; it carries no weights or Compose dependency. Do not introduce another ONNX Runtime/native copy without reconciling ownership.

Default model-only tests use SDK factories for a sample-preset roundtrip and a caller-selected Local-file configuration. They synthesize Kitten PCM and transcribe it with Whisper without opening the microphone. The post-extraction native/configuration cases passed on Pixel 9 / Android 17; see [current verification](../../docs/development.md). This establishes native inference, not physical audio quality or broad accuracy.

## Opt-in physical audio and UI journeys

Unlock the phone, disconnect external audio devices and use a quiet space. To enable the physical speaker-to-microphone test:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.acoustic=true
```

The test synthesizes a fixed sentence, plays it with AudioTrack, records through AudioRecord and transcribes captured PCM. It grants this app microphone access if needed, temporarily sets media volume to half maximum, then restores volume. It never bypasses the lock screen. Logs include duration, rate/count, RMS, peak and transcript. The acceptance condition checks non-silence and at least four of six content words; it is not a human speech-accuracy benchmark.

The UI journey is separately opt-in. Set an audible media volume yourself and grant microphone access before starting. Install both APKs, then invoke instrumentation directly; the Gradle connected suite can uninstall the application at the end and lose its permission grant:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:installDebug :sample:androidApp:installDebugAndroidTest
adb -s YOUR_SERIAL shell pm grant dev.debanshu.speech.sample android.permission.RECORD_AUDIO
adb -s YOUR_SERIAL shell am instrument -w -r -e demo true \
  -e class io.github.debanshu.siren.sample.android.SpeechUiDemoTest \
  dev.debanshu.speech.sample.test/androidx.test.runner.AndroidJUnitRunner
```

It drives the actual controller through capture, transcription and playback with a repeatable physical speaker stimulus. It does not inject transcript text/phases, change volume, grant permission itself or use accessibility automation. The current direct-instrumentation run passed with all six checked content words recognized; see [capture evidence](../../docs/demo.md). This emits sound and opens the microphone. Keep its observed result separate from the earlier acoustic run below, which recognized “brown” as “round.”

## Historical evidence

The following files record pre-extraction sample runs on 2026-11-21:

- [Native smoke](verification/native-smoke-2026-11-21.json): model-only PCM roundtrip.
- [Physical acoustic run](verification/acoustic-roundtrip-2026-11-21.json): microphone/speaker path passed; “brown” was recognized as “round,” so the result was not word-perfect. Both the initial stricter failure and later acceptance are preserved.
- [Actual UI journey](verification/ui-journey-2026-11-21.json): recorded controller/UI path and exact transcript.
- [Package-rename native rerun](verification/package-rename-native-smoke-2026-11-21.json): in-memory roundtrip with renamed classes.

These records preserve original test identities. Current extraction/build evidence is in [development](../../docs/development.md); rerun relevant hardware checks for changed artifacts. Instrumentation outputs are generated under this module's `build/outputs/androidTest-results/connected/debug`.

## Recent Changes

Reused the SDK bundled model layout without duplicating file mappings; documented the default Android engine factory while retaining explicit sample injection and existing hardware verification scope.
