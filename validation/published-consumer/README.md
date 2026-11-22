# Published Siren consumer

This standalone fixture uses Kotlin 2.4.21, AGP 9.5.1 and the same Gradle wrapper
as Siren. It has no Compose plugin or dependency. Both its KMP library and its
independent Android app depend only on `io.github.debanshu777:siren:0.1.0`.
An optional `-PsirenVersion=...` selects another version. This version is prepared
locally; its presence here does not establish a public Central release.

By default, Maven Central is the exclusive source for the Siren group.
`-PsirenRepository=/absolute/path` instead selects a file Maven repository as the
exclusive source for that group. The fixture has no `mavenLocal`, SDK project
dependency, included build, substitution or checkout-native linker/header paths.
Google and Maven Central provide ordinary Kotlin/Android dependencies.

## Reproduce outside the checkout

The root task `./gradlew validatePublishedSirenConsumer` automates local staging,
copying and all host-supported checks below, including the JVM model roundtrip.
It uses no publishing credentials. The manual recipe is:

First publish **locally** from the Siren repository root:

```sh
./gradlew :siren:publishAllPublicationsToStagingRepository
```

Copy this fixture and the staged repository to two new directories outside the
checkout. For example, from the Siren root:

```sh
cp -R validation/published-consumer /private/tmp/siren-published-consumer-check
cp -R build/staging-repository /private/tmp/siren-published-consumer-repository
cd /private/tmp/siren-published-consumer-check
```

Use fresh destination directories rather than copying over earlier results. Set
`ANDROID_HOME` to an existing SDK containing platform 37 and build tools 37.0.0.
Full Xcode and an Apple Silicon Mac are required for both iOS framework checks.
Run:

```sh
./gradlew -PsirenRepository=/private/tmp/siren-published-consumer-repository \
  --offline jvmTest compileAndroidMain :androidApp:assembleDebug \
  linkDebugFrameworkIosSimulatorArm64 linkDebugFrameworkIosArm64
```

`--offline` requires Gradle plugins, the wrapper distribution and ordinary
Kotlin/Android dependencies to be cached already. The copied file repository is
read directly; the fixture does not download models or native runtimes.

## What these checks establish

- JVM tests compile and execute third-party recognizer/synthesizer implementations
  against the published SPI, including independent capabilities and configurable
  model descriptors.
- Android compiles the published platform factory; the plain Android app builds
  an APK containing the SDK's transitive JNI payload, without a separate sherpa
  dependency or copied SDK AAR.
- iOS compiles platform factory and inference entry points into **dynamic**
  frameworks, forcing native symbols to resolve. The fixture supplies no sherpa,
  ONNX Runtime or checkout `-F`/`-L` flags. The SDK's cinterop artifacts must supply
  their own native archive bytes and system linkage.

The default checks do not run speech models. To test the published JVM native payload with consumer-owned models, also copy the prepared model directory outside the SDK checkout, then run from the relocated fixture:

```sh
./gradlew -PsirenRepository=/absolute/path/to/copied/staging-repository \
  -Pnative=true -PmodelsDir=/absolute/path/to/copied/models --offline jvmTest
```

The opt-in check stages these files as consumer classpath resources, synthesizes and transcribes using the published SDK JAR, then closes the engine. It does not install apps, record microphones or verify audible output. A successful dynamic link establishes dependency closure
for these built targets; it is not a public Maven release or a compatibility
claim for arbitrary toolchains.

## Check a publicly delivered release

After a version is publicly available, run from a relocated copy of this fixture:

```sh
./gradlew --refresh-dependencies -PsirenVersion=0.1.0 \
  -Pnative=true -PmodelsDir=/absolute/path/to/copied/models \
  jvmTest compileAndroidMain :androidApp:assembleDebug \
  linkDebugFrameworkIosSimulatorArm64 linkDebugFrameworkIosArm64
```

Do not supply `sirenRepository` or `--offline` for this check. The root
`validateCentralSirenConsumer` task performs this online check after verifying
Central delivery of the configured version, copying only the fixture and
consumer-owned models outside the checkout. It neither stages nor publishes SDK
artifacts. The prepared release workflow requires it before creating a GitHub
release. This release-only check has not been run against a public `0.1.0`.

Expected artifacts are `build/test-results/jvmTest/`,
`androidApp/build/outputs/apk/debug/androidApp-debug.apk` and
`build/bin/<iosTarget>/debugFramework/SirenConsumer.framework`.

The [recorded verification](../published-consumer-verification.json) passed all of
these checks using a copied local snapshot repository, plus the opt-in JVM native roundtrip with consumer classpath models (24 kHz, 59,693 samples, six content words recognized). It records artifact hashes,
Android native entries and both iOS dynamic-library dependency lists. UIKit is a
default iOS system link in the pinned Kotlin/Native toolchain, even for this plain
consumer; the fixture and SDK do not introduce Compose or UIKit-facing APIs.

## Recent Changes

Added separate staged and Central-only consumer modes with typed Gradle runners;
the Central mode refreshes online resolution and supplies no local SDK repository.
