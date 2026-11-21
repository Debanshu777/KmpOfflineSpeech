# Shared speech app

`commonMain` contains the speech contract, controller and Compose screen. `androidMain` implements the sherpa Kotlin API and native audio. `iosMain` uses sherpa's C API plus an AVFoundation bridge. The JVM target exists for common controller tests only.

Kotlin sources live under `kotlin/com/debanshu777/speech` in each source set. The Android library namespace and Kotlin framework bundle identifier are `com.debanshu777.speech.shared`; generated C bindings use `com.debanshu777.speech.native` as declared in `src/iosMain/cinterop/speech.def`.

Android compilation depends on `prepareAndroidSpeech`, which supplies the local AAR and generated model assets. Each iOS cinterop/framework build depends on the corresponding Gradle audio preparation task. See the [root README](../README.md), [architecture](../docs/architecture.md), and [verification guide](../docs/development.md) for setup, ownership rules and limits.

## Recent Changes

Renamed Kotlin packages, source paths and generated iOS C bindings to `com.debanshu777.speech`. The focused recorder UI and Gradle preparation flow retain their behavior.
