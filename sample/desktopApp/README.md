# Siren Desktop sample

This JVM host displays the shared `SirenApp` screen and controller. Its JavaSound
adapter lives in `sample/composeApp/src/jvmMain`; inference belongs to the UI-free
SDK through an injected `createJvmSpeechEngine(SampleModels.bundledDemo())` engine.
Compose Desktop and `kotlinx-coroutines-swing` are dependencies of this host only.

From the repository root:

```sh
./gradlew :sample:desktopApp:run
./gradlew :sample:desktopApp:createDistributable
```

The first build prepares pinned demo weights and SDK native runtime inputs through
Gradle. `stageDesktopModels` copies the explicit sample preset under classpath
`models/` resources; `SampleModels.bundledDemo()` delegates to the SDK's
`SherpaModels.bundled()` descriptor, and the SDK resolves those paths through the
class loader. An app packaging this layout can use `createSpeechEngine()` with
no per-file configuration. The SDK contains no model weights and downloads none.

`createDistributable` builds an application image for the current host OS. For
example, macOS produces
`sample/desktopApp/build/compose/binaries/main/app/Siren.app`. Native installers
are produced only on their corresponding operating systems; building here does
not verify another OS/architecture.

Packaging needs a full supported JDK with `jpackage`, `jlink` and `jmods`. Compose's
packaging check rejects the Homebrew JDK distribution on macOS; select an existing
full vendor JDK with `JAVA_HOME=/path/to/jdk` when invoking the packaging command.
The ordinary development build and model-only check do not require an installer.

## Audio and lifecycle

Recording uses the default JavaSound microphone with mono signed 16-bit PCM,
preferring 16 kHz and falling back to 48 or 44.1 kHz. The adapter preserves the
actual selected rate. Both its recording buffer and shared controller cap clips
at 20 seconds. Playback opens the default output at the synthesized rate.

JavaSound has no portable microphone permission preflight. The operating system
checks access when Record opens an input line; on macOS allow Siren in Privacy &
Security → Microphone if requested. The packaged app declares its microphone
usage description. Development `run` uses the selected JDK/launcher, whose OS
permission identity may differ from the packaged app. No permission settings
are changed automatically.

Capture/playback run on background workers. Stop/cancellation closes audio lines
to release blocked reads, writes and drains; window close awaits engine ownership
and audio cleanup before exiting. Hardware availability, exclusive device access,
Bluetooth routes and operating-system audio behavior still require manual checks.

## Model-only verification

```sh
./gradlew :sample:desktopApp:nativeSpeechSmoke
```

This loads the desktop's actual classpath model resources and SDK runtime, creates
Kitten PCM, transcribes it with Whisper and checks three expected words. It opens
neither microphone nor speaker. It is not evidence of audible output, physical
microphone capture or speech-recognition accuracy across arbitrary recordings.

The [macOS arm64 result](verification/macos-arm64-2026-11-22.json) records a passing
development-classpath roundtrip, a second roundtrip using only the packaged
image's jars from outside the checkout, and successful native-image packaging
with a full JetBrains JDK 21. The native executable was started; visual launch
confirmation is tracked separately. JavaSound hardware paths remain unverified.

## Recent Changes

Reused the SDK's standard bundled descriptor for desktop model resources and
documented the default factory; audio and model-only verification scopes remain separate.
