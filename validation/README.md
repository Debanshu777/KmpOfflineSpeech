# Artifact validation

`published-consumer` is an independent, non-Compose Kotlin Multiplatform project.
It resolves Siren only through published Maven coordinates, using Maven Central
by default or an explicitly supplied local repository. It has no SDK project dependency, included build,
dependency substitution, model download tasks or checkout-native search paths.

See its [README](published-consumer/README.md) for the relocation and verification
commands. `./gradlew validatePublishedSirenConsumer` stages the configured version
locally and relocates the fixture, repository and models before testing. It does
not publish a public Maven release. The separate `validateCentralSirenConsumer`
task verifies an already-published version against Central, with refreshed online
dependency resolution and no local Siren repository.

The recorded [artifact-consumer result](published-consumer-verification.json)
passed offline after copying the consumer and Maven repository outside the SDK
checkout: four JVM tests (including the macOS arm64 native roundtrip), Android compilation/APK packaging and dynamic iOS
framework links for both arm64 targets. Native runtime symbols resolved without
checkout framework paths; the Android APK included its JNI payload and no models.

The earlier unpublished `0.1.0` candidate has separate [local release-preparation evidence](release-preparation-0.1.0.json), including API baselines, 20 build-tool tests, notice/source-availability packaging, four consumer JVM tests and both iOS links. Earlier snapshot evidence remains unchanged.

The [quick-start model consumer](models-consumer/README.md) validates automatic SDK dependency setup and default/opt-out model packaging with the public SDK. Its plugin can be resolved from staged implementation/marker artifacts outside this checkout. The add-on 0.1.0 is independently published; [release evidence](models-publication-0.1.0.json) includes public plugin-ID resolution. [Default/opt-out verification](default-models-verification.json) records the latest automatic SDK setup and packaging checks.

## Recent Changes

Recorded model add-on 0.1.0 public Central delivery, candidate JAR identity and staged/public consumer verification. Historical local evidence remains unchanged.
