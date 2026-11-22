# Siren agent instructions

Siren is a UI-free Kotlin Multiplatform speech SDK with a separate teaching sample. Keep it independent of CaraML infrastructure. Do not add Compose, UI/components, microphone permissions, hardware recording/playback or model weights to `:siren`. Keep all such application concerns in `sample/`. No camera/vision structure or generic AI framework is requested. Tooling stays in Gradle/Kotlin; do not add Python setup scripts.

## Modules and packages

- `:siren` / `siren/src`: explicit public API, PCM, model configuration, providers and Android/iOS inference. Root package `io.github.debanshu.siren`; `audio` holds PCM and `speech` holds contracts/engines. SDK C bindings use `.internal.native`.
- `:sample:composeApp` / `sample/composeApp`: controller, Compose screen, permissions and hardware audio; packages `.sample` and `.sample.platform`. Sample C bindings use `.sample.internal.audio`.
- `:sample:androidApp`: launcher and tests in `.sample.android`.
- `sample/iosApp`: SwiftUI Siren host consuming `SirenApp.framework`.
- `:sample:desktopApp`: Compose Desktop launcher and classpath model resources. Shared JVM audio is JavaSound in `sample/composeApp/src/jvmMain`, not in the SDK.
- `buildSrc`: typed verified downloads, safe extraction, runtime preparation and static-archive cinterop bundling.
- `scripts/dependencies.json`: artifact URLs, hashes, notices and provenance. Generated binaries, weights and caches remain ignored.

Mirror packages in directories. Preserve Android application ID `dev.debanshu.speech.sample` and iOS host bundle ID `dev.debanshu.offlinespeech`. Historical verification files retain original identifiers.

Models are independently optional. Android, iOS and JVM factories require explicit `SherpaModels`; `SampleModels.bundledDemo()` belongs only to the sample. JVM bundled files are classpath resources; its engine owns materialized temporary copies, while Local files remain caller-owned. New model families implement `SpeechRecognizer`/`SpeechSynthesizer` and concrete configuration. Audio/UI must use capabilities and labels rather than assume Whisper/Kitten. Descriptors do not download weights. A null provider does not imply a smaller package.

## Native distribution and validation

Android `prepareAndroidRuntime` embeds the pinned upstream wrapper JAR, arm64-v8a/x86_64 JNI payloads and notices in the SDK. `prepareAndroidSpeech` supplies only sample model assets. Do not reintroduce an app-local AAR dependency or a second ONNX Runtime payload without reconciling ownership.

`prepareJvmRuntime` embeds the official JVM wrapper classes, notices and six native resource sets under `sherpa-onnx/native/`: osx-aarch64/osx-x64, linux-aarch64/linux-x64 and win-arm64/win-x64. Upstream code selects/extracts the runtime without checkout-native paths. macOS arm64 and Linux x64 (CI) native inference are verified; packaging/hash checks do not establish execution on the other four targets.

SDK iOS cinterop KLIBs embed sherpa/ONNX static archives. Sample audio cinterop embeds its AVFoundation archive. Keep generated definitions and archive inputs declared in Gradle; the Xcode host must not depend on raw native checkout paths. Do not infer portable distribution from an in-repository link alone.

Relevant commands from the repository root:

```bash
./gradlew checkSpeechSetup :siren:jvmTest :sample:composeApp:jvmTest
./gradlew :sample:androidApp:assembleDebug
./gradlew :siren:compileKotlinIosSimulatorArm64
./gradlew :sample:desktopApp:nativeSpeechSmoke
./gradlew :sample:desktopApp:createDistributable
./gradlew :siren:publishAllPublicationsToStagingRepository
```

Local staging is `io.github.debanshu777:siren:0.1.0-SNAPSHOT` in `build/staging-repository`, with version controlled by `gradle.properties`. No public release is available. Keep public publication separate from local staging. Preserve and rerun relocated Maven consumer checks after artifact changes, including native JVM payloads; target-specific API/ABI evidence remains a release gate. See `docs/library-design.md`.

Use `--offline` after required Gradle/speech artifacts are cached. SDK runtime tasks do not depend on model preparation; sample builds do. Root tasks include `prepareSpeechModels`, `prepareAndroidRuntime`, `prepareJvmRuntime`, `prepareAndroidSpeech`, `prepareIosSpeech` and the two target-specific `prepareIosAudio...` tasks. `checkSpeechSetup` runs the special `buildSrc` build's tests. The SDK JVM native check is opt-in: `:siren:jvmTest -Pnative=true -PmodelsDir=/absolute/model/root`; default JVM tests do not load models. Desktop smoke tests do not verify physical JavaSound input/output.

Desktop packaging requires a full JDK accepted by Compose/jpackage. Its app version `1.0.0` satisfies macOS packaging constraints and is separate from SDK snapshot coordinates. Preserve packaging guards; do not claim execution on a platform from resource inclusion or an unexecuted CI job.

Build the checked-in Siren Xcode scheme for iOS validation. Android model-only tests use `:sample:androidApp:connectedDebugAndroidTest`. That connected suite can uninstall the app afterward, so run the opt-in UI journey through installed APKs and direct instrumentation:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:installDebug :sample:androidApp:installDebugAndroidTest
adb -s YOUR_SERIAL shell pm grant dev.debanshu.speech.sample android.permission.RECORD_AUDIO
adb -s YOUR_SERIAL shell am instrument -w -r -e demo true \
  -e class io.github.debanshu.siren.sample.android.SpeechUiDemoTest \
  dev.debanshu.speech.sample.test/androidx.test.runner.AndroidJUnitRunner
```

Require an unlocked phone, built-in audio devices and an existing audible media volume. The UI test does not change volume, grant permission itself or use accessibility automation; it never injects transcripts/phases. The separate acoustic fixture has its own opt-in prerequisites. See `docs/development.md` and `docs/demo.md` for exact evidence and limits.

Every native allocation needs clear ownership and paired destruction. Cancellation must wait for synchronous native work before teardown. Do not claim microphone/speaker verification, accuracy or performance from compilation or an in-memory model roundtrip.

Update the root README, affected module READMEs and relevant docs with every code/build/tooling change, including concise Recent Changes entries. Report checks actually run and distinguish pre-extraction evidence from checks of current artifacts.
