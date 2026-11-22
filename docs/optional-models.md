# Package the default models during your build

The optional [Siren model Gradle plugin](../siren-models-gradle-plugin/README.md) removes manual model-file placement. Apply it to the app's KMP module, and its normal build obtains and packages the selected Whisper/Kitten files. The app can use them offline from its first launch.

**Status:** this add-on is prepared locally from source and is not published. It works with the already-published `io.github.debanshu777:siren:0.1.0` engine. Do not add a versioned plugin declaration expecting it to resolve from a public repository yet.

Include the plugin source build in `pluginManagement` as shown in its [quickstart](../siren-models-gradle-plugin/README.md#use-from-source), then add this to the shared module's existing `plugins` block:

```kotlin
id("io.github.debanshu777.siren.models")
```

Keep the normal Siren dependency and engine calls. Both default bundles are selected automatically, using the known `models/whisper` and `models/kitten` paths.

To package only synthesis:

```kotlin
sirenModels { recognition.set(false) }
```

Use the matching runtime configuration, `SherpaModels.bundled(recognition = false)`, when creating the engine. Selecting files during the build and configuring engine capabilities are separate operations.

The first build fetches pinned model files, verifies their bytes and packages them for the app. It preserves the compressed Kitten download and reuses a verified cache. The core SDK still contains no weights, and the running app gains no downloader or network permission. Models use ordinary files after packaging/extraction; compression does not remove their storage or memory requirements.

Android/JVM packaging is wired into generated assets/resources. Standard iOS Xcode direct integration runs the model-copy task automatically; custom CocoaPods/SwiftPM integration needs an app-build resource-copy step. The plugin README details the supported setup and directory ownership.

See the [independent consumer](../validation/models-consumer/README.md) and [development notes](development.md) for verification and its limits.

## Recent Changes

Added a source-only optional model-packaging path for apps that want the default models ready offline after installation.
