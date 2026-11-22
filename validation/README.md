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

The unpublished `0.1.0` candidate has separate [local release-preparation evidence](release-preparation-0.1.0.json), including API baselines, 20 build-tool tests, notice/source-availability packaging, four consumer JVM tests and both iOS links. Earlier snapshot evidence remains unchanged.

## Recent Changes

Added published-consumer probes for default factories, standard model layouts,
automatic scoped close and the raw PCM transcription overload, retaining legacy
factory coverage and separate local-staging/Central gates.

Added a [Central credential check](central-check/README.md) using only read-only GET requests; authenticated namespace access is checked without uploading artifacts.

Added an independent [signing-secret probe](signing-check/README.md) and manual
workflow. It checks Gradle key/passphrase compatibility and public-key verification
without publishing SDK artifacts or authenticating Maven Central credentials.
Protected keys require a passphrase; explicit unprotected mode uses none.
