# Shared speech app

`commonMain` contains the speech contract, controller and Compose screen. `androidMain` implements the sherpa Kotlin API and native audio. `iosMain` uses sherpa's C API plus an AVFoundation bridge. The JVM target exists for common controller tests only.

Android compilation depends on `prepareAndroidSpeech`, which supplies the local AAR and generated model assets. Each iOS cinterop/framework build depends on the corresponding Gradle audio preparation task. See the [root README](../README.md), [architecture](../docs/architecture.md), and [verification guide](../docs/development.md) for setup, ownership rules and limits.

## Recent Changes

Added a compact focused recorder screen with a circular record/stop control, actual elapsed time, a prominent transcript and contextual playback Stop. Platform preparation remains managed by Gradle.
