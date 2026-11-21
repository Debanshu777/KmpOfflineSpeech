# Development and verification

Use JDK 21+, Android SDK platform 37.0/build tools 37.0.0, and full Xcode for iOS. The checked-in Gradle wrapper manages the build. Typed tasks in `buildSrc` download verified artifacts and compile the native audio bridge before the platform tasks that consume them.

```bash
./gradlew checkSpeechSetup :composeApp:jvmTest :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme OfflineSpeech \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
git diff --check
```

Use `./gradlew prepareAndroidSpeech` or `./gradlew prepareIosSpeech` when preparing artifacts alone. Normal platform builds infer those dependencies. `--offline` works after both Gradle dependencies and the selected runtime/model downloads have been cached. Task outputs are declared, so unchanged preparation is up to date. See [build tools](../buildSrc/README.md) for task inputs, extraction checks and cache behavior.

The JVM target exercises the common controller; it is not a desktop speech implementation. Its tests cover permission denial, recording timeout and cancellation waiting for native ownership. `checkSpeechSetup` runs the build-tool regression suite for checksum/size verification, cached downloads, extraction and publication. CI uses the Ubuntu runner's installed Android tools, installs the pinned SDK, then runs these tests and builds Android with Gradle.

## Device tests

First-party packages now start with `com.debanshu777.speech`. For class-filtered instrumentation runs, use `com.debanshu777.speech.android.SpeechUiDemoTest` as shown in the [capture notes](demo.md). Historical verification JSON keeps its original `dev.debanshu` test identifiers; it is a record of those runs rather than a current invocation template. The app IDs remain stable; see [architecture](architecture.md#packages-and-installation-identities).

The default connected suite performs a model-only roundtrip. The acoustic test is explicitly enabled because it emits sound and opens the microphone:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :androidApp:connectedDebugAndroidTest
```

See [Android host notes](../androidApp/README.md) for the exact acoustic opt-in command and recorded results. Keep the phone unlocked on a quiet desk with its built-in microphone and speaker available. The test restores media volume after use. It never changes the lock state. A successful acoustic smoke test establishes the audio path, not word-perfect recognition or an accuracy benchmark.

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

Verified on 2026-11-21:

- Gradle build-tool regression suite: 10 tests passed.
- Shared JVM controller suite: 3 tests passed with the Gradle-only build.
- The tutorial's minimal Compose screen compiled against the real controller for JVM and iOS simulator using Gradle. The excerpt is documentation; the app continues to use the focused recorder screen.
- Gradle prepared the Android model assets/AAR from verified cache and compiled the device/simulator iOS audio bridges.
- Pixel 9 / Android 17 model-only and acoustic roundtrip tests: 2 passed. The acoustic transcript was “The quick round fox jumps over the lazy dog.” The 16 kHz microphone captured 3.680 seconds of non-silent audio played through the 24 kHz output path. See the [Android host notes](../androidApp/README.md) for exact diagnostics and acceptance criteria.
- iOS native C API roundtrip passed; [recorded result](ios-native-smoke.json) and [reproduction](ios-notes.md#native-runtime-smoke-test). This test does not call the Kotlin UI adapter or microphone/speaker.
- The opt-in [real UI journey](../androidApp/verification/ui-journey-2026-11-21.json) passed one test with zero failures. The screen/controller recorded the physical speaker stimulus, transcribed “The quick brown fox jumps over the lazy dog.”, generated speech, played it and returned to Ready. The [GIF](demo.md) captures this run; no transcript or phase was injected.
- The refined iOS simulator host built and launched through the Gradle-managed native preparation flow; its actual screen is captured in the demo notes.
- A development-signed physical iPhone build succeeded, but installation was rejected because the device's free-development app slots were full. No existing apps were removed. Physical iPhone microphone/playback remains unverified; simulator verification is the selected scope for now.

Device microphone/playback observations are recorded separately in the Android host notes, including the exact recognized text and the acoustic test's acceptance criterion. Do not replace a measured transcription with the expected phrase.

After the package rename on the same date, the Gradle checks passed: 10 preparation regressions, 3 shared controller tests, Android app and instrumentation APK builds, and the full iOS simulator host build. The class-filtered native test also passed on Pixel 9 under `com.debanshu777.speech.android.NativeSpeechSmokeTest`; see its [new result](../androidApp/verification/package-rename-native-smoke-2026-11-21.json). Microphone/playback was not repeated for this namespace refactor; the earlier acoustic and UI evidence above remains the record of those checks.

## Updating dependencies

Update URL, hash and size together in `scripts/dependencies.json`, verify upstream provenance/licenses, then run the Gradle preparation and platform checks. Arbitrary ONNX graphs cannot replace these bundles solely because they end in `.onnx`.

Generated Android model assets now live under `composeApp/build/generated/speechAssets`. When updating a checkout prepared by the earlier setup tools, remove the old ignored `composeApp/src/androidMain/assets/models` copy once to avoid duplicate assets. Do not commit model weights, runtime archives, `local.properties`, signing data or build directories.
