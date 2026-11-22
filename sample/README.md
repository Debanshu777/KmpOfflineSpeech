# Siren sample

The sample demonstrates the UI-free [`:siren` SDK](../siren/README.md). It owns the controller, Compose screen, microphone permissions, hardware capture/playback, model acquisition and packaged demonstration weights.

- [`:sample:composeApp`](composeApp/README.md): shared presentation plus platform audio hosts.
- [`:sample:androidApp`](androidApp/README.md): Android launcher and instrumentation tests.
- [`sample/iosApp`](iosApp/README.md): SwiftUI host consuming `SirenApp.framework`.
- [`:sample:desktopApp`](desktopApp/README.md): Compose Desktop launcher with JavaSound audio from the shared JVM sample source set.

`SampleModels.bundledDemo()` explicitly configures Whisper tiny and Kitten nano. Pass that descriptor to `createAndroidSpeechEngine(context, models)`, `createIosSpeechEngine(models)` or `createJvmSpeechEngine(models)`, then inject the engine into the sample audio host. The SDK has no default weights or preset. See [model configuration](../docs/model-configuration.md).

## Run

From the repository root:

```bash
./gradlew :sample:androidApp:assembleDebug
open sample/iosApp/iosApp.xcodeproj
./gradlew :sample:desktopApp:run
```

Run Android through Android Studio or the [root instructions](../README.md#run-the-android-sample). In Xcode choose the Siren scheme and an iOS 18.5+ arm64 simulator. Physical iPhones require a signing team.

Normal builds prepare their own dependencies. Android assets are generated under `sample/composeApp/build/generated/speechAssets`; iOS bundles root `models`. Desktop `stageDesktopModels` copies the sample weights into its classpath `models/` resources. `prepareAndroidRuntime`, `prepareIosSpeech` and `prepareJvmRuntime` are runtime-only SDK tasks; only sample preparation adds weights.

Desktop uses JavaSound's default input/output. The OS checks microphone access on opening input; the packaged macOS app declares a purpose string. Development JDK permissions can differ. Packaging requires a full JDK accepted by Compose/jpackage and is host-OS-specific; see the [desktop README](desktopApp/README.md). Desktop audio hardware remains manually unverified, even when model-only smoke tests pass.

The macOS arm64 application image built with full JBR 21. The application packaging version is `1.0.0`, independently of SDK `0.1.0-SNAPSHOT`; other OS installers and physical desktop audio are not verified by that build.

## Limits and verification

Record up to 20 seconds, stop and transcribe, edit, then Speak. TTS input is capped at 500 characters. The sample is foreground-only; it has no production model installer, streaming or automatic route recovery. Runtime omission of a capability does not remove its packaged demonstration assets.

```bash
./gradlew :sample:composeApp:jvmTest
./gradlew :sample:androidApp:connectedDebugAndroidTest
./gradlew :sample:desktopApp:nativeSpeechSmoke
```

Connected acoustic/UI journeys require explicit opt-in and a suitable unlocked device. The UI journey uses installed APKs and direct instrumentation after a separate microphone grant; it does not change media volume. Desktop native smoke opens no audio devices. See [development](../docs/development.md) and [capture notes](../docs/demo.md) for evidence and exact commands.

## Recent Changes

Added a separate desktop host, classpath model staging and JavaSound audio alongside the Android/iOS samples; all application concerns remain outside the SDK.
