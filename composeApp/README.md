# Shared speech app

`commonMain` contains the speech contract, controller and Compose screen. `androidMain` implements the sherpa Kotlin API and native audio. `iosMain` uses sherpa's C API plus an AVFoundation bridge. The JVM target exists for common controller tests only.

See the [root README](../README.md), [architecture](../docs/architecture.md), and [verification guide](../docs/development.md) for setup, ownership rules and limits.

## Recent Changes

Added shared foreground speech UI, native platform adapters and cancellation ownership tests.
