# Artifact validation

`published-consumer` is an independent, non-Compose Kotlin Multiplatform project.
It resolves Siren only through published Maven coordinates from an explicitly
supplied local repository. It has no SDK project dependency, included build,
dependency substitution, model download tasks or checkout-native search paths.

See its [README](published-consumer/README.md) for the relocation and verification
commands. These checks stage snapshots locally; they do not publish a public
Maven release.

The recorded [artifact-consumer result](published-consumer-verification.json)
passed offline after copying the consumer and Maven repository outside the SDK
checkout: four JVM tests (including the macOS arm64 native roundtrip), Android compilation/APK packaging and dynamic iOS
framework links for both arm64 targets. Native runtime symbols resolved without
checkout framework paths; the Android APK included its JNI payload and no models.

## Recent Changes

Added a relocated published-artifact consumer with JVM SPI tests, Android builds
and dynamic iOS framework linking, plus an opt-in published JVM native/model-resource check.
