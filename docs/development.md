# Development, verification and releases

Use JDK 21+, Android SDK platform 37.0/build tools 37.0.0, and full Xcode on Apple Silicon for iOS. Desktop packaging additionally needs a full JDK accepted by Compose/jpackage; see the [desktop host guide](../sample/desktopApp/README.md). The Gradle wrapper and build files pin tool versions.

## Source and API gate

From the repository root:

```bash
./gradlew checkSpeechSetup :siren:checkKotlinAbi :siren:jvmTest \
  :sample:composeApp:jvmTest :sample:androidApp:assembleDebug \
  :sample:androidApp:assembleDebugAndroidTest :sample:desktopApp:jvmJar
git diff --check
```

`:siren` uses explicit API mode. `checkKotlinAbi` compares public surfaces with `siren/api/android/siren.api`, `siren/api/jvm/siren.api` and `siren/api/siren.klib.api`. For an intentional public change, review compatibility and update with `:siren:updateKotlinAbi`; do not regenerate baselines merely to hide an unexpected failure. A text/API check does not establish every binary or behavioral compatibility property.

`checkSpeechSetup` invokes the separate `buildSrc` test build. Its regressions cover hashes, sizes, cache reuse, safe extraction and publication. SDK common tests exercise optional providers and ownership; sample tests cover permissions, limits, capability-aware controls and cancellation cleanup.

SDK runtime tasks `prepareAndroidRuntime`, `prepareJvmRuntime` and `prepareIosSpeech` do not prepare weights. Sample builds declare their own model dependencies. Network preparation occurs in typed task actions. `--offline` is useful after required Gradle and selected artifact inputs have been cached. See [build tools](../buildSrc/README.md).

## Native and application checks

SDK JVM native inference requires separately prepared compatible models:

```bash
./gradlew prepareSpeechModels
./gradlew :siren:jvmTest -Pnative=true -PmodelsDir="$(pwd)/models"
./gradlew :sample:desktopApp:nativeSpeechSmoke
```

Default SDK JVM tests skip the opt-in native case. Native smoke tests do not open audio hardware. macOS arm64 and Linux x64 have recorded execution evidence; four other packaged desktop classifiers remain untested.

For iOS:

```bash
./gradlew :sample:composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project sample/iosApp/iosApp.xcodeproj -scheme Siren \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Cinterop KLIBs embed native archive bytes. The Xcode host links the shared framework and system libraries, not raw checkout-native paths. A standalone direct C API diagnostic is described in [iOS notes](ios-notes.md#native-runtime-smoke-test); it has a different scope from Kotlin SDK or microphone tests.

Android model-only instrumentation:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:connectedDebugAndroidTest
```

The real UI journey needs an unlocked phone, built-in audio devices, a pre-granted microphone permission and existing audible media volume. Connected Gradle tests can uninstall the sample afterward, so install and invoke this journey directly:

```bash
ANDROID_SERIAL=YOUR_SERIAL ./gradlew :sample:androidApp:installDebug :sample:androidApp:installDebugAndroidTest
adb -s YOUR_SERIAL shell pm grant dev.debanshu.speech.sample android.permission.RECORD_AUDIO
adb -s YOUR_SERIAL shell am instrument -w -r -e demo true \
  -e class io.github.debanshu.siren.sample.android.SpeechUiDemoTest \
  dev.debanshu.speech.sample.test/androidx.test.runner.AndroidJUnitRunner
```

It uses actual callbacks/inference/audio, without injecting text/phases, accessibility automation or volume changes. See [capture notes](demo.md) and [Android host instructions](../sample/androidApp/README.md) for separate acoustic opt-in behavior. Permission denial, cancellation, timeout, backgrounding and offline behavior should also be checked manually on supported devices.

## Optional model-packaging add-on

The standalone [Gradle plugin](../siren-models-gradle-plugin/README.md) is a local, unpublished add-on. Run `./gradlew -p siren-models-gradle-plugin check` for integrity/extraction and functional build regressions. The [independent consumer](../validation/models-consumer/README.md) tests actual generated resources against the published SDK. Its `verifyModelPackaging` task hashes the selected files inside the Android APK, JVM JAR and iOS resource output. The [model workflow](../.github/workflows/models-check.yml) consumes a staged plugin marker outside the checkout; it contains no publication step. Existing core release artifacts and their evidence remain unchanged.

Local verification passed 16 plugin regressions and five relocated consumer tests, including real synthesis/transcription using automatically generated JVM resources. Both iOS dynamic frameworks linked. All seven model hashes matched in Android/JVM/iOS outputs; synthesis-only selection retained only its four files. The actual Kotlin Xcode embed task copied the selected models into an app resource directory, preserved another app resource and reused configuration cache. This was a packaging fixture, not an installed-app or physical-audio check. The new remote workflow has not run. See [recorded results](../validation/optional-models-verification.json).

## Artifact and release validation

Version comes from `sirenVersion` in root `gradle.properties`. Version 0.1.0 is [published to Maven Central](https://central.sonatype.com/artifact/io.github.debanshu777/siren/0.1.0); see the [release verification](releasing.md). Repository-local staging remains usable:

```bash
./gradlew :siren:publishAllPublicationsToStagingRepository
```

This writes `build/staging-repository` and can run unsigned without Central credentials. Vanniktech publication configuration produces root/target metadata, source/documentation artifacts and native cinterop publications. Central publication requires signing; do not disable signing or package validation to make a release pass.

The [independent consumer](../validation/published-consumer/README.md) must run against copied candidate artifacts outside the checkout, without project substitution or native checkout paths. The fixture accepts `-PsirenVersion=0.1.0` and an explicit `-PsirenRepository` directory. Check JVM native execution with separately supplied classpath models, plain Android APK contents, both iOS dynamic links, notices and absence of weights/Compose dependencies in SDK artifacts. Historical snapshot evidence is not a substitute for testing a changed candidate.

The [check workflow](../.github/workflows/check.yml) runs Ubuntu/macOS source/API, host-native and relocated-consumer checks; macOS also builds the desktop image. The prepared [release workflow](../.github/workflows/release.yml) is manual, main-only and concurrency-serialized. Preflight matches the stable property, rejects an existing coordinate/tag and extracts matching changelog notes. Secrets are scoped to the credential-presence check and signed-publish step, not ordinary builds/consumer checks. Do not print their values or commit keys. `verifyNativeRedistribution` must pass before upload.

Release management uses typed Gradle tasks:

| Task | Purpose/report |
| --- | --- |
| `validateSirenRelease` | Main/SHA/version/tag/Central-absence checks; `build/reports/release/version.json` and curated notes |
| `validatePublishedSirenConsumer` | Local staging plus isolated fixture/repository/models; `build/reports/validation/published-consumer.json` |
| `verifyNativeRedistribution` | Fail-closed notice/provenance audit; `build/reports/native-redistribution.json` |
| `verifySirenCentralPublication` | Public root/target artifacts, metadata, sources, documentation and signatures; `build/reports/release/central-delivery.json` |
| `validateCentralSirenConsumer` | Separate relocated Central-only consumer with refreshed dependencies; `build/reports/validation/central-consumer.json` |

For local candidate testing, `./gradlew validatePublishedSirenConsumer` stages and copies its own inputs. On macOS it checks both iOS targets as well as JVM/Android; other hosts validate supported non-Apple publications. The release workflow reserves each version tag only after validation, signs/publishes to Central, verifies public delivery, and runs `validateCentralSirenConsumer` before creating the GitHub release. That consumer resolves Siren from Central without a local/custom SDK repository or offline mode, refreshing dependencies. A failed post-reservation release requires inspecting the existing tag/deployment rather than blindly rerunning reservation.

A successful upload or Central validation is not proof that public downloads are available. Follow the deployment to its final published state, then resolve the public root and platform artifacts before marking the release available. Vanniktech documents these [separate Central stages](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).

The current technical native inventory passes with 33 pinned notice texts, source availability and compiler reviews for both NDKs and both MSVC toolsets. `verifyNativeRedistribution` also validates the compiler-evidence checksum; missing component/payload/compiler reviews or changed vendored bytes fail closed. [Third-party status](../THIRD_PARTY_NOTICES.md) records the scope and downstream obligations. The release workflow now defaults to a nonpublishing preflight; publication, tag and release steps require `publishRelease=true`.

For a future explicitly authorized version, configure the [repository publishing/signing secrets](releasing.md#github-release-procedure), commit/push the reviewed main branch and dispatch the manual release with the matching `confirmVersion`, key protection and `publishRelease=true`. Never upload an existing Central version again. The [release guide](releasing.md) explains readiness and retry behavior. The release record must identify candidate checks, deployment and public-resolution results before availability claims change.

## Signing-secret CI check

The separate [Central credential workflow](../.github/workflows/central-check.yml)
runs the [read-only Gradle check](../validation/central-check/README.md) to verify
token authentication and namespace access with no deployment or publication.

The manual [Check signing secrets](../.github/workflows/signing-check.yml) workflow
runs from `main` with your full public signing fingerprint as input. Its isolated
[Gradle fixture](../validation/signing-check/README.md) signs a harmless text probe
using the saved key ID, ASCII-armored private key and passphrase. GnuPG independently
verifies it against the key downloaded from Ubuntu's keyserver and checks the exact
fingerprint. Select `passphrase` (default) or explicitly `unprotected` when
dispatching. The latter uses an empty key password and ignores `SIGNING_PASSWORD`;
protected keys require it. The other four secret names must be nonempty. The Central token's
authentication is not tested.

This check has read-only repository permissions, disables Gradle build/configuration
caches, scopes secrets to the signing step and runs no publication, tag or release
commands. It does not build the SDK/native runtimes or establish release readiness.
See the workflow run's job summary for the observed result. The full SDK release
pipeline has separate source, native, signing, delivery and Central-consumer gates; see [release verification](releasing.md).


Observed result: [signing-only CI run 45055771902](https://github.com/Debanshu777/Siren/actions/runs/45055771902) passed in explicit unprotected mode. Gradle signed the probe and GnuPG verified it against the expected published public key. `SIGNING_PASSWORD` was unused; Central credentials were checked for presence only. No publication, release or tag ran. See the [nonsecret verification record](../validation/signing-check-verification.json).

## Recorded verification

These records describe their stated artifacts/runs, not every future release:

| Check | Observed result and scope |
| --- | --- |
| SDK/sample extraction | SDK 6/common and sample 5 JVM tests; preparation 10 regressions; Android app/test APKs built |
| Android SDK native/configuration | Two model-only cases passed on Pixel 9/Android 17 |
| Android real UI journey | Actual speaker-to-microphone recording, transcription and playback passed; [evidence](../sample/androidApp/verification/siren-sdk-ui-2026-11-22.json) |
| Relocated Maven consumer | Four JVM tests including macOS native inference, Android APK with eight JNI entries/no models, both dynamic iOS links; [evidence](../validation/published-consumer-verification.json) |
| macOS arm64 JVM | Native SDK roundtrip passed; [evidence](../siren/verification/jvm-native-macos-arm64-2026-11-22.json) |
| Linux x64 JVM | Native inference passed in [GitHub CI](https://github.com/Debanshu777/Siren/actions/runs/45044942797); [recorded result](../siren/verification/jvm-native-linux-x64-2026-11-22.json) |
| macOS desktop sample | Source and packaged-JAR model checks, application image and actual window launch/inspection passed; [evidence](../sample/desktopApp/verification/macos-arm64-2026-11-22.json). Hardware audio unverified |
| iOS sample | Moved host built, installed and launched on iPhone 17/iOS 27 simulator; [capture](demo.md). Physical iPhone audio unverified |

Earlier [Android acoustic evidence](../sample/androidApp/verification/acoustic-roundtrip-2026-11-21.json) recognized “brown” as “round”; preserve the observed text rather than replacing it with the expected sentence. Other historical runs and captures remain linked from [Android notes](../sample/androidApp/README.md). A model roundtrip is neither a human accuracy benchmark nor proof of microphone/speaker operation. A signed iPhone build was produced earlier, but installation was rejected because free-development app slots were full; no existing apps were removed.

## Dependency maintenance

Update artifact URL, hash, size and provenance together in `scripts/dependencies.json`. Review redistribution notices and test the resulting payloads. Generated native libraries, weights, caches, local SDK paths, signing data and build outputs stay ignored. No separate setup interpreter is needed.

## Recent Changes

Completed native compiler notice/evidence coverage, added vendored-input integrity regressions and a default nonpublishing preflight. Central authentication/namespace access passed in the [read-only CI check](https://github.com/Debanshu777/Siren/actions/runs/45056250019); [nonsecret result](../validation/central-credential-verification.json).


Verified the shorter factories, standard model-layout helpers, raw PCM call and cancellation-safe scoped cleanup through source tests and relocated artifacts. Updated the API baselines intentionally; no remote publication ran.

Added a manually dispatched signing-secret verification path with explicit key
protection modes, a public-key fingerprint check and no SDK publication.

## Simpler integration verification — 2026-11-22

The revised API passed 16 SDK common tests and five sample controller tests. The opt-in SDK native test was skipped in the ordinary unit run; native inference ran separately in the relocated Maven consumer. That consumer passed four API tests and one actual macOS arm64 model roundtrip using `createSpeechEngine().use { ... }` and `transcribe(samples, sampleRate)`. It synthesized 59,930 samples at 24 kHz and recognized all six checked words. This verifies model execution, not microphone/playback hardware or general recognition accuracy.

Android/JVM/native API baselines passed. The independent consumer built a plain Android APK and linked dynamic frameworks for both iOS arm64 targets. Android sample app/test APKs and the desktop sample JAR also built successfully. See [the local verification record](../validation/simple-integration-0.1.0.json). Existing device captures describe earlier runs; this API change did not repeat physical audio testing. At the time of that integration check, candidate `0.1.0` was unpublished; later release results are recorded below.

## Local release preparation — 2026-11-22

The unpublished `0.1.0` candidate passed 20 build-tool regression tests, Android/JVM/native API baseline checks, local Maven publication with sources/Dokka documentation, and a relocated artifact consumer. That consumer passed four JVM tests (including actual classpath-model inference), Android APK packaging and both dynamic iOS framework links. The Android AAR, JVM JAR and both native cinterop KLIBs each contain 21 pinned license texts and the verified source-availability statement, with no model weights. See [recorded checks and artifact hashes](../validation/release-preparation-0.1.0.json).

A Central-command dry-run confirmed mandatory signing and the native redistribution dependency; it executed no publication. The readiness gate correctly rejected the remaining Android compiler-runtime notice packaging and Windows static CRT/STL provenance gaps. Actual signing with maintainer keys, Central delivery/consumer checks, remote workflows and Medium edits were deferred at the user’s request.

## Publication readiness — 2026-11-23

These readiness notes predate the publication recorded below.

The completed native/compiler inventory passed its mandatory gate. Local checks passed 23 build-tool regressions, 16 SDK common tests, five sample tests and five relocated-consumer tests, including native JVM inference and both iOS dynamic links. The ordinary SDK native opt-in test remained skipped; native execution was verified by the separate consumer. All 30 target publication signatures were independently verified. Android AAR, JVM JAR and both iOS cinterop KLIBs each contain 33 hash-verified notice texts and the current source-availability statement, with no model weights. Android sample app/test APKs, desktop JAR and iOS simulator framework also rebuilt. See [candidate hashes and results](../validation/publication-readiness-0.1.0.json). Full GitHub preflight subsequently passed; at that point no publication had run.

Android minimum SDK is 27 throughout the library, samples and isolated consumer. The pinned ONNX Runtime Android build uses `--android_api 27`, corroborated by the bundled ELF Android API notes; declaring 26 would understate that build floor.

Relocated staging consumers resolve build-tool and non-Siren dependencies online by default; an explicit outer `--offline` flag is propagated. Siren coordinates remain exclusively bound to the copied repository, so allowing plugin downloads cannot substitute a public SDK artifact.

Final CI passed on `75602ac`: [Linux/macOS platform checks](https://github.com/Debanshu777/Siren/actions/runs/45059952805) and [nonpublishing preflight](https://github.com/Debanshu777/Siren/actions/runs/45059983741). The preflight built the iOS sample framework, linked both relocated iOS consumers, packaged the desktop app and executed all five candidate signing tasks. Tag reservation, Central upload and release creation were skipped. Final checks found no `v0.1.0` tag and returned HTTP 404 for the Central POM. [CI result record](../validation/ci-publication-readiness-0.1.0.json). At that preflight, Central upload-time validation and Central-only consumption were still pending; the publication section below records their final results.

## Maven Central publication — 2026-11-23

Siren 0.1.0 was published from `75602ac` through the [release workflow](https://github.com/Debanshu777/Siren/actions/runs/45062849601). Public root/target artifacts, metadata, source/documentation archives and signature files returned HTTP 200. A separate Central-only consumer refreshed dependencies and passed JVM native inference, Android APK packaging and both iOS dynamic framework links. See [publication evidence](../validation/central-publication-0.1.0.json) and the [GitHub release](https://github.com/Debanshu777/Siren/releases/tag/v0.1.0). Earlier candidate records above remain historical. README and Medium integration examples use the published coordinate and scoped `createSpeechEngine` API.
