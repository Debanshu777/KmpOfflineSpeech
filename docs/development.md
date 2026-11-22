# Development and verification

Use JDK 21+, Android SDK platform 37.0/build tools 37.0.0, and full Xcode for iOS. The checked-in Gradle wrapper manages the build. Typed tasks in `buildSrc` download verified artifacts and compile the native audio bridge before the platform tasks that consume them.

```bash
./gradlew checkSpeechSetup :siren:jvmTest :sample:composeApp:jvmTest :sample:androidApp:assembleDebug
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project sample/iosApp/iosApp.xcodeproj -scheme Siren \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
git diff --check
```

Use `./gradlew prepareAndroidSpeech` or `./gradlew prepareIosSpeech` when preparing artifacts alone. Normal platform builds infer those dependencies. `--offline` works after both Gradle dependencies and the selected runtime/model downloads have been cached. Task outputs are declared, so unchanged preparation is up to date. See [build tools](../buildSrc/README.md) for task inputs, extraction checks and cache behavior.

The SDK JVM target tests provider composition, optional capabilities, configuration validation and native ownership rules. Its built-in desktop inference adapter has an explicit opt-in native test; see the [SDK evidence](../siren/verification/jvm-native-macos-arm64-2026-11-22.json). The sample JVM tests cover permission denial, recording timeout, capability-aware controls and cancellation waiting for native ownership. `checkSpeechSetup` runs the build-tool regression suite for checksum/size verification, cached downloads, extraction and publication. CI uses the Ubuntu runner's installed Android tools, installs the pinned SDK, then runs these tests and builds Android with Gradle. A separate opt-in JVM native roundtrip uses those prepared models to exercise the Linux desktop payload in CI.

## Device tests

First-party packages now start with `io.github.debanshu.siren`. For class-filtered instrumentation runs, use `io.github.debanshu.siren.sample.android.SpeechUiDemoTest` as shown in the [capture notes](demo.md). Historical verification JSON keeps its original `dev.debanshu` test identifiers; it is a record of those runs rather than a current invocation template. The app IDs remain stable; see [architecture](architecture.md#packages-and-installation-identities).

The default connected suite performs model-only roundtrips using the sample preset and caller-supplied local paths/options. The acoustic test is explicitly enabled because it emits sound and opens the microphone:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:connectedDebugAndroidTest
```

See [Android host notes](../sample/androidApp/README.md) for the exact acoustic opt-in command and recorded results. Keep the phone unlocked on a quiet desk with its built-in microphone and speaker available. The UI fixture requires an audible volume and leaves it unchanged. It never changes the lock state. A successful acoustic smoke test establishes the audio path, not word-perfect recognition or an accuracy benchmark.

## Manual recipe

1. Tap Record and deny microphone access. Confirm an error and no recording. Enable access in Settings and return.
2. Record a clear English phrase for 2–5 seconds, then stop. Confirm editable recognized text.
3. Tap Speak. Confirm the edited text is played and the screen returns to Ready.
4. Start playback and cancel. Repeat recording, transcription and synthesis after cancellation.
5. Record without tapping Stop; confirm the elapsed-time ring and 20-second automatic stop.
6. Background the app during recording/playback. Return and confirm it is ready. Cleanup during inference may wait for native work.
7. Repeat without network connectivity. Android's merged manifest has no Internet permission; iOS reads models from its bundle.

Capture the actual app. See [demo and capture notes](demo.md); generated mockups are not evidence of app behavior.

## Verification status

### SDK/sample extraction — 2026-11-22

- `:siren:jvmTest`: 6 tests passed; `:sample:composeApp:jvmTest`: 5 tests passed.
- `checkSpeechSetup`: 10 preparation regression tests passed.
- Android sample and instrumentation APKs assembled after the split. The SDK native roundtrip and configurable Local-file roundtrip passed on Pixel 9 / Android 17.
- `:siren:publishAllPublicationsToStagingRepository` generated local Maven publications; no remote Maven release was made. Artifact inspection found the wrapper JAR, supported JNI libraries and runtime notices in the Android AAR, no model weights, and no Compose dependencies in SDK module metadata. The iOS cinterop KLIB includes both native static archives.
- The real UI journey passed in 16.947 seconds after Gradle installation and a microphone permission grant: the actual microphone captured the physical speaker stimulus, the screen showed “The quick brown fox jumps over the lazy dog.”, Speak played the recognized text and returned to Ready. The fixture uses live Compose callbacks without Espresso, accessibility automation or injected transcript/phase; see [result](../sample/androidApp/verification/siren-sdk-ui-2026-11-22.json) and [capture recipe](demo.md#reproduce-the-ui-journey). Earlier fixture attempts failed before the flow because of Android 17 test-framework/automation conflicts; those were test setup failures, not successful UI checks.
- [Relocated artifact-consumer validation](../validation/published-consumer-verification.json) passed after JVM support was added: four JVM tests, including a real macOS arm64 model roundtrip using consumer classpath files and packaged SDK natives (24 kHz, 59,693 samples, all six expected content words), a plain Android APK and both dynamic iOS frameworks. It used copied local Maven artifacts outside this checkout, with no SDK project substitution or native checkout paths.
- `:sample:composeApp:linkDebugFrameworkIosSimulatorArm64` and the moved Xcode Siren scheme built successfully. The app installed and launched in the iPhone 17 / iOS 27 simulator; the [current screenshot](demo.md) shows the actual sample.

- Desktop: the shared screen compiled and the macOS arm64 distributable built using full JBR 21. A classpath-model smoke and a second check using only the packaged app’s JARs passed (24 kHz, 59,936 and 59,898 samples respectively, exact test sentence). The packaged app launched and its actual screen was inspected; [evidence](../sample/desktopApp/verification/macos-arm64-2026-11-22.json) and [screenshot](demo.md). Desktop microphone and speaker hardware remain unverified.

- [GitHub CI](https://github.com/Debanshu777/Siren/actions/runs/45044942797) passed the Android/shared gate and the separate Linux x64 native model roundtrip on commit `8eb37bb`; [recorded result](../siren/verification/jvm-native-linux-x64-2026-11-22.json). It opens no audio devices.

### Earlier sample verification — 2026-11-21

Verified before the SDK extraction:

- Gradle build-tool regression suite: 10 tests passed.
- Shared JVM controller suite: 3 tests passed with the Gradle-only build.
- The tutorial's minimal Compose screen compiled against the real controller for JVM and iOS simulator using Gradle. The excerpt is documentation; the app continues to use the focused recorder screen.
- Gradle prepared the Android model assets/AAR from verified cache and compiled the device/simulator iOS audio bridges.
- Pixel 9 / Android 17 model-only and acoustic roundtrip tests: 2 passed. The acoustic transcript was “The quick round fox jumps over the lazy dog.” The 16 kHz microphone captured 3.680 seconds of non-silent audio played through the 24 kHz output path. See the [Android host notes](../sample/androidApp/README.md) for exact diagnostics and acceptance criteria.
- iOS native C API roundtrip passed; [recorded result](ios-native-smoke.json) and [reproduction](ios-notes.md#native-runtime-smoke-test). This test does not call the Kotlin UI adapter or microphone/speaker.
- The opt-in [real UI journey](../sample/androidApp/verification/ui-journey-2026-11-21.json) passed one test with zero failures. The screen/controller recorded the physical speaker stimulus, transcribed “The quick brown fox jumps over the lazy dog.”, generated speech, played it and returned to Ready. The [GIF](demo.md) captures this run; no transcript or phase was injected.
- The refined iOS simulator host built and launched through the Gradle-managed native preparation flow; its actual screen is captured in the demo notes.
- A development-signed physical iPhone build succeeded, but installation was rejected because the device's free-development app slots were full. No existing apps were removed. Physical iPhone microphone/playback remains unverified; simulator verification is the selected scope for now.

Device microphone/playback observations are recorded separately in the Android host notes, including the exact recognized text and the acoustic test's acceptance criterion. Do not replace a measured transcription with the expected phrase.

After the package rename on the same date, the Gradle checks passed: 10 preparation regressions, 3 shared controller tests, Android app and instrumentation APK builds, and the full iOS simulator host build. The class-filtered native test also passed on Pixel 9 under the package recorded in its [new result](../sample/androidApp/verification/package-rename-native-smoke-2026-11-21.json). Microphone/playback was not repeated for this namespace refactor; the earlier acoustic and UI evidence above remains the record of those checks.

## Signing-secret CI check

The manual [Check signing secrets](../.github/workflows/signing-check.yml) workflow
runs from `main` with your full public signing fingerprint as input. Its isolated
[Gradle fixture](../validation/signing-check/README.md) signs a harmless text probe
using the saved key ID, ASCII-armored private key and passphrase. GnuPG independently
verifies it against the key downloaded from Ubuntu's keyserver and checks the exact
fingerprint. All five required secret names must be nonempty. The Central token's
authentication is not tested.

This check has read-only repository permissions, disables Gradle build/configuration
caches, scopes secrets to the signing step and runs no publication, tag or release
commands. It does not build the SDK/native runtimes or establish release readiness.
See the workflow run's job summary for the observed result. The full SDK release
pipeline remains separately prepared locally until publication work resumes.

## Recent Changes

Added a manually dispatched signing-secret verification path with a public-key
fingerprint check and no SDK publication.

## Updating dependencies

Update URL, hash and size together in `scripts/dependencies.json`, verify upstream provenance/licenses, then run the Gradle preparation and platform checks. Arbitrary ONNX graphs cannot replace these bundles solely because they end in `.onnx`.

Generated Android model assets now live under `sample/composeApp/build/generated/speechAssets`. When updating a checkout prepared by the earlier setup tools, remove the old ignored `composeApp/src/androidMain/assets/models` copy once to avoid duplicate assets. Do not commit model weights, runtime archives, `local.properties`, signing data or build directories.
