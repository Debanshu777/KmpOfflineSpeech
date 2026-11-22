# Siren guides

Use [getting started](getting-started.md) to configure models and call Siren's explicit Android/iOS/JVM factories. Siren 0.1.0 is available on [Maven Central](https://central.sonatype.com/artifact/io.github.debanshu777/siren/0.1.0). See [release status](library-design.md).

To understand the native integration itself, follow the [model integration tutorial](medium-draft.md). To run the completed application, use the [sample hosts](../sample/README.md). The [demo](demo.md) contains actual Android, iOS and desktop captures and their verification limits.

- [Install and make the first inference call](getting-started.md)
- [Default model setup and opt-out, published Gradle plugin](optional-models.md)
- [Configure or replace model providers](model-configuration.md)
- [SDK and sample architecture](architecture.md)
- [Native distribution and release status](library-design.md)
- [Development, API checks and verification](development.md)
- [Release verification and publishing](releasing.md)
- [iOS native integration and diagnostic](ios-notes.md)
- [Actual captures and hardware checks](demo.md)
- [Changelog](../CHANGELOG.md)
- [Third-party licenses and notices](../THIRD_PARTY_NOTICES.md)

The SDK has no UI, Compose dependency, microphone/playback implementation or model weights. Applications own those concerns; the sample shows one implementation. Generated runtime binaries/weights remain ignored in the source repository, while platform SDK artifacts package required native runtime code and notices.

## Recent Changes

Added the published model add-on quick-start with default SDK/model setup and opt-out.
