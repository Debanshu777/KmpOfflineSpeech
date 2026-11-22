# Shared Siren sample

`:sample:composeApp` owns the controller, Compose screen, `SampleModels.bundledDemo()`, microphone permissions and platform capture/playback. It consumes `:siren` for PCM, public contracts, model configuration and native inference. It is a sample application module, not the SDK publication.

`commonMain` uses `io.github.debanshu.siren.sample`; `.sample.platform` audio hosts live in Android, iOS and JVM source sets. SDK inference types live under `.audio` and `.speech` in [`siren/`](../../siren/README.md). The sample Android namespace/framework bundle ID is `io.github.debanshu.siren.sample.shared` and its Swift framework is `SirenApp`.

`SampleModels.bundledDemo()` is a one-line wrapper around `SherpaModels.bundled()`. Create an Android, iOS or JVM SDK engine with that descriptor and inject it into the corresponding audio host. Apps using the same bundled layout can use `createSpeechEngine(context)` on Android or `createSpeechEngine()` on iOS/JVM. Other providers need no UI rewrite: the sample uses capabilities and display labels. The controller owns platform/engine close. See [configuration](../../docs/model-configuration.md).

## Generated inputs and builds

From the repository root:

```bash
./gradlew :sample:composeApp:jvmTest
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64
```

`prepareAndroidSpeech` supplies sample-only model assets under this module's `build/generated/speechAssets`. SDK wrapper/JNI libraries come through the `:siren` dependency. Default sample builds package both demo bundles even if runtime configuration omits a provider.

On iOS, `src/iosMain/cinterop/audio.def` generates `.sample.internal.audio` bindings. The selected `prepareIosAudio...` task compiles the AVFoundation archive; `bundleAudioCinterop...` embeds it in a KLIB. SDK runtime archives arrive through SDK cinterop KLIBs. Framework linkage uses Apple system libraries and no raw native checkout search paths. Sample framework links also prepare root `models` for Xcode resource copying.

`jvmMain` supplies `JvmSpeechPlatform`, a JavaSound adapter with bounded mono PCM16 capture, default-device output and actual sample-rate preservation. It delegates inference to `createJvmSpeechEngine`, keeps blocking audio on workers and closes lines/joins workers during cancellation. Its model descriptor uses the SDK's standard bundled layout; the sample supplies the actual files. The [desktop launcher](../desktopApp/README.md) adds Compose Desktop and coroutines-swing, stages classpath weights and awaits cleanup before exit.

The JVM target also runs controller tests. Model-only native smoke checks do not establish physical desktop microphone/playback behavior. Recording limits, lifecycle behavior and evidence are in [architecture](../../docs/architecture.md) and [development](../../docs/development.md).

## Recent Changes

Reduced the sample model descriptor to the SDK's bundled preset, retaining injected engines, existing model labels and platform audio ownership.
