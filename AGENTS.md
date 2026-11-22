# Siren agent instructions

Siren is a UI-free Kotlin Multiplatform speech SDK with a separate teaching sample. Keep it independent of CaraML infrastructure. Do not add Compose, UI/components, microphone permissions, hardware recording/playback or model weights to `:siren`. Keep all such application concerns in `sample/`. No camera/vision structure or generic AI framework is requested. Tooling stays in Gradle/Kotlin; do not add Python setup scripts.

## Modules and packages

- `:siren` / `siren/src`: explicit public API, PCM, model configuration, providers and Android/iOS/JVM inference. Root package `io.github.debanshu.siren`; `audio` holds PCM and `speech` holds contracts/engines. SDK C bindings use `.internal.native`.
- `:sample:composeApp` / `sample/composeApp`: controller, Compose screen, permissions and hardware audio; packages `.sample` and `.sample.platform`. Sample C bindings use `.sample.internal.audio`.
- `:sample:androidApp`: launcher and tests in `.sample.android`.
- `sample/iosApp`: SwiftUI Siren host consuming `SirenApp.framework`.
- `:sample:desktopApp`: Compose Desktop launcher and classpath model resources. Shared JVM audio is JavaSound in `sample/composeApp/src/jvmMain`, not in the SDK.
- `buildSrc`: typed verified downloads, safe extraction, runtime preparation and static-archive cinterop bundling.
- `scripts/dependencies.json`: artifact URLs, hashes, notices and provenance. Generated binaries, weights and caches remain ignored.

Mirror packages in directories. Preserve Android application ID `dev.debanshu.speech.sample` and iOS host bundle ID `dev.debanshu.offlinespeech`. Historical verification files retain original identifiers.

Basic integration uses `createSpeechEngine(context)` on Android and `createSpeechEngine()` on iOS/JVM, defaulting to `SherpaModels.bundled()`. The user-requested SDK helpers `bundled(directory="models", recognition=true, synthesis=true, threads=2)` and `local(directory, recognition=true, synthesis=true, threads=2)` intentionally describe the standard Whisper tiny/Kitten nano layout. These are file/configuration presets only: no weights, hidden downloads, eager model loading, UI or global Android context. `SampleModels.bundledDemo()` is a sample wrapper, not a required SDK dependency.

Recognition/synthesis remain independently optional. Keep original named factories with default model arguments and retain custom descriptors/SPI for advanced model families. JVM bundled files are classpath resources; materialized copies are engine-owned while Local files remain caller-owned. Audio/UI uses capabilities and labels. Runtime omission does not imply a smaller package.

Prefer suspending `engine.use { engine -> ... }` for short-lived examples; it closes in NonCancellable cleanup and preserves an operation failure with any cleanup failure suppressed. Import `speech.use` and `speech.transcribe` when showing scoped/direct-PCM calls. `transcribe(samples, sampleRate)` accepts caller-owned mono FloatArray plus actual rate; capture/playback stay in the application. Long-lived controllers own one engine and close at disposal. Do not introduce extra factory-wrapper classes, seven-path mapping or manual finally blocks into the basic quickstart.

## Native distribution and validation

Android `prepareAndroidRuntime` embeds the pinned upstream wrapper JAR, arm64-v8a/x86_64 JNI payloads and notices in the SDK. `prepareAndroidSpeech` supplies only sample model assets. Do not reintroduce an app-local AAR dependency or a second ONNX Runtime payload without reconciling ownership.

`prepareJvmRuntime` embeds the official JVM wrapper classes, notices and six native resource sets under `sherpa-onnx/native/`: osx-aarch64/osx-x64, linux-aarch64/linux-x64 and win-arm64/win-x64. Upstream code selects/extracts the runtime without checkout-native paths. macOS arm64 and Linux x64 (CI) native inference are verified; packaging/hash checks do not establish execution on the other four targets.

SDK iOS cinterop KLIBs embed sherpa/ONNX static archives. Sample audio cinterop embeds its AVFoundation archive. Keep generated definitions and archive inputs declared in Gradle; the Xcode host must not depend on raw native checkout paths. Do not infer portable distribution from an in-repository link alone.

Relevant commands from the repository root:

```bash
./gradlew checkSpeechSetup :siren:checkKotlinAbi :siren:jvmTest :sample:composeApp:jvmTest
./gradlew :sample:androidApp:assembleDebug
./gradlew :siren:compileKotlinIosSimulatorArm64
./gradlew :sample:desktopApp:nativeSpeechSmoke
./gradlew :sample:desktopApp:createDistributable
./gradlew :siren:publishAllPublicationsToStagingRepository
```

Version comes from `sirenVersion` in root `gradle.properties`; candidate `0.1.0` targets `io.github.debanshu777:siren:0.1.0`. It is prepared locally and not published. The user authorized Central credential verification and resolving release readiness, including source/CI preparation. Do not upload to Central, publish, reserve tags, create releases or change Medium. The release workflow defaults to `publishRelease=false`; keep all mutating release steps behind that explicit input. Local staging writes `build/staging-repository` and can run unsigned without publishing credentials. Central configuration uses Vanniktech, signing, POM metadata and Dokka. Preserve source/API, redistribution-readiness and relocated consumer gates. See `docs/development.md` and `docs/library-design.md`.

Vendored compiler notices under `scripts/notices/` are declared Gradle inputs and verified by exact hashes; never weaken the native gate. `scripts/native-compiler-evidence.json` records original public build logs, source pins and byte correspondence and has its own manifest checksum.

API baselines live under `siren/api`. Run `:siren:checkKotlinAbi`; use `:siren:updateKotlinAbi` only for reviewed intentional public API changes, not to hide unexpected compatibility failures. Release notes come from the exact matching `CHANGELOG.md` version section. Keep install snippets and release status aligned with the candidate/result.

When publication resumes, use typed Gradle tasks `validateSirenRelease`, `validatePublishedSirenConsumer`, `verifyNativeRedistribution`, `verifySirenCentralPublication` and `validateCentralSirenConsumer` through the main-only manual workflow. Preserve collision checks, staging and isolated validation before tag reservation/signing/upload, then public delivery/Central-only consumer checks before GitHub release creation. Never bypass provenance/notice holds or signing. Keep secrets scoped to credential check/publish. Do not add duplicate approval after explicit user resumption. Upload or local staging alone never establishes public availability.

`docs/releasing.md` records the five repository secret names, deferred future release steps and current native-readiness hold. Keep secret values out of source/logs/chat; the local prepared workflow is not evidence of an executed remote release.

Use `--offline` after required Gradle/speech artifacts are cached. SDK runtime tasks do not depend on model preparation; sample builds do. Root tasks include `prepareSpeechModels`, `prepareAndroidRuntime`, `prepareJvmRuntime`, `prepareAndroidSpeech`, `prepareIosSpeech` and the two target-specific `prepareIosAudio...` tasks. `checkSpeechSetup` runs the special `buildSrc` build's tests. The SDK JVM native check is opt-in: `:siren:jvmTest -Pnative=true -PmodelsDir=/absolute/model/root`; default JVM tests do not load models. Desktop smoke tests do not verify physical JavaSound input/output.

Desktop packaging requires a full JDK accepted by Compose/jpackage. Its app version `1.0.0` satisfies macOS packaging constraints and is separate from SDK Maven coordinates. Preserve packaging guards; do not claim execution on a platform from resource inclusion or an unexecuted CI job.

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

`validation/signing-check` is an independent Gradle signing-only probe. Its manual
`signing-check.yml` workflow runs from main with an expected public fingerprint,
checks the required secret names, signs using in-memory PGP credentials, and verifies
against the published public key. It has no publishing plugin or repositories.
Keep credential values out of logs/artifacts/caches. Maven credential presence is
not authentication verification, and a passed probe is not SDK release readiness.
Protected mode requires SIGNING_PASSWORD; explicitly selected unprotected mode
uses an empty password and leaves key/secret values unchanged.

`validation/central-check` verifies Central authentication and namespace access
using a fixed HTTPS GET endpoint with a 401 unauthenticated control. It has no
deployment/publication calls. Never print credentials, derived authorization
headers or response bodies; retain only status/result metadata. The manual
`central-check.yml` scopes the two Maven secrets to that one check step.

Update the root README, affected module READMEs and relevant docs with every code/build/tooling change, including concise Recent Changes entries. Report checks actually run and distinguish pre-extraction evidence from checks of current artifacts.

Preserve the canonical formatting of Kotlin-generated ABI references. `.gitattributes` allows their generated final blank line for Android/JVM dumps; do not trim it to satisfy a general whitespace check.
