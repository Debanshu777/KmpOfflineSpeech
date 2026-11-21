# Development and verification

Use JDK 21+, Python 3.10+, Android SDK 37, and full Xcode for iOS. The Gradle wrapper is checked in. Runtime binaries and models must be generated with `scripts/setup.py` before building the relevant platform.

```bash
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew :composeApp:jvmTest :androidApp:assembleDebug
python3 scripts/prepare_ios.py
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme OfflineSpeech \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
git diff --check
```

The JVM target exercises the common controller only; it is not a desktop speech implementation. The tests verify permission denial, the recording duration limit, and cancellation waiting for native ownership before teardown. Python tests cover hash/size verification, safe archive extraction and verified download caching. CI builds Android and runs the shared/Python tests; local iOS linkage must be checked with Xcode.

## Manual recipe

1. Install a clean build; tap Record and deny microphone access. Confirm an error and no recording. Enable access in system Settings and return.
2. Record a clear English phrase for 2–5 seconds, then stop. Confirm editable recognized text.
3. Tap Speak. Confirm a natural voice speaks the edited text and returns to Ready.
4. Start speaking and cancel. Repeat a recording, a transcription and synthesis after cancellation.
5. Record without tapping Stop; confirm automatic stop at 20 seconds.
6. Background the app during recording and playback. Return and confirm it is ready. During inference, cleanup may wait for native work.
7. With networking disabled, repeat transcription and synthesis. The Android app has no Internet permission; iOS model paths come only from the app bundle.

Capture screenshots from the actual app. Do not use generated mockups to claim working behavior. Record platform/device, command, outcome and limitations below; compilation alone is not a microphone or speaker test.

## Verification status

Verified on 2026-11-21:

- Python setup regression tests: 10 passed.
- Shared JVM controller tests: 3 passed.
- Android debug APK: built successfully.
- Pixel 9 / Android 17 native Kitten-to-Whisper test: 1 passed; exact phrase recognized. See [recorded evidence](../androidApp/verification/native-smoke-2026-11-21.json).
- iOS arm64 device and simulator framework/host builds: passed (device build unsigned); launched and visually verified on iPhone 17 / iOS 27 simulator. See [capture notes](demo.md).

The device test does not exercise microphone capture or audible speaker output. These manual checks remain unverified. The simulator screen confirms launch; model inference and audio routing are separate checks.

## Updating dependencies

Change pinned Gradle versions intentionally. For downloaded artifacts, update URL/hash/size together in `scripts/dependencies.json`, verify upstream provenance and licenses, re-run setup and both platform builds. Model payloads must match the runtime's expected format; arbitrary ONNX graphs cannot be substituted solely because they end in `.onnx`.

The setup cache stores only verified completed downloads. Extraction rejects traversal, symlinks and special files. Do not commit model weights, runtime ZIPs, `local.properties`, signing data or build directories.
