# Optional Siren model packaging

This quick-start Gradle plugin supplies the Siren SDK dependency and packages Whisper tiny INT8 and Kitten nano INT8 by default. It packages selected files into Android assets and JVM resources, and copies them into iOS app resources through Kotlin's standard Xcode direct integration.

The plugin is a **local, unpublished add-on**. Siren SDK 0.1.0 is already on Maven Central; this plugin is not part of that release. Its JAR contains build logic, a pinned catalog and a license text, not model weights. The core SDK remains unchanged.

## Use from source

In your application's `settings.gradle.kts`, include this plugin build:

```kotlin
pluginManagement {
    includeBuild("/absolute/path/to/Siren/siren-models-gradle-plugin")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
```

Apply it to the KMP module that supplies your app's speech engine:

```kotlin
plugins {
    // Your existing Kotlin Multiplatform / Android plugins.
    id("io.github.debanshu777.siren.models")
}
```

The plugin adds the published Siren 0.1.0 dependency automatically (`api` in KMP commonMain, `implementation` in Java). Ensure `mavenCentral()` is configured in your application dependency repositories; no separate Siren dependency declaration is needed. Both model bundles are selected by default. Keep using `createSpeechEngine(context)` on Android and `createSpeechEngine()` on iOS/JVM. Your app still owns audio input/output and permissions.

## Opt out or customize

To supply your own files while retaining automatic SDK setup:

```kotlin
sirenModels { bundleModels.set(false) }
```

This performs no model downloads and contributes no generated model files to Android/JVM packaging. The iOS model-copy task is skipped so it does not overwrite app-owned resources. Clean old app build products once when switching away from previously bundled models. Supply `SherpaModels.local(...)`, your own bundled files or custom providers to the engine as needed. Alternatively, remove the plugin and declare the core SDK dependency yourself.

The default SDK version can be selected in one place:

```kotlin
sirenModels { sdkVersion.set("0.1.0") }
```

For recognition only:

```kotlin
sirenModels { synthesis.set(false) }
```

Also configure the SDK with `SherpaModels.bundled(synthesis = false)` when creating the engine. For synthesis only, set `recognition` to false in both configurations. Packaging selection controls files; the SDK configuration controls available operations.

## How packaging works

- Android: apply to the module using `com.android.kotlin.multiplatform.library`. Generated assets flow into the host APK through its dependency on that module. Android asset support is enabled by the plugin. Plain Android application/library plugins are not supported in this first version.
- JVM: generated `models/` resources are included in KMP `jvmMain` or Java `main`. The existing SDK classpath resolver reads them.
- iOS: the plugin makes `embedAndSignAppleFrameworkForXcode` depend on `copySirenModelsForXcode`. Under Xcode, `TARGET_BUILD_DIR` and `UNLOCALIZED_RESOURCES_FOLDER_PATH` select the app's `models/` directory. The task owns only that generated directory. Keep manual model resources out of the same destination. CocoaPods/SwiftPM or custom integration must invoke the resource-copy task from the app build phase themselves; applying a plugin cannot make a KLIB carry app resources automatically.

Outside Xcode, `copySirenModelsForXcode` stages files under `build/generated/siren-models/ios/models`. This supports inspection and custom app packaging; it does not prove an installed iOS app contains them.

The Xcode phase must actually invoke the Gradle integration task. If an IDE-specific script skips it with `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED`, run `copySirenModelsForXcode` in a separate app build phase so model resources are still copied. Custom Xcode configurations must also provide a framework build type as required by Kotlin.

Model acquisition happens only when a resource-packaging task needs the output. The first build needs network access unless the exact pinned files are cached. `--offline` rejects missing/corrupt cache entries without contacting a model server. The plugin uses a project-local `.gradle/siren-models-cache`; `cacheDirectory` can select another verified cache.

Downloads are verified by SHA-256 and size. Kitten's compressed archive is safely extracted and its individual files reverified. Updating selection replaces generated output, removing stale unselected model folders. No runtime downloader, Internet permission or engine initialization step is added.

The two bundles occupy approximately 138 MB before app/archive compression. Quantized model bytes must remain exact; archive compression reduces transfer/storage size, and the runtime still needs ordinary readable files. Weight/data licenses are independent of Siren's source license; the plugin preserves supplied notices and adds Whisper's MIT license and model provenance.

## Checks and local staging

From the Siren root:

```bash
./gradlew -p siren-models-gradle-plugin/buildSrc test
./gradlew -p siren-models-gradle-plugin check verifyModelAddonArtifacts
```

This tests the build plugin and stages its implementation and plugin-marker artifacts locally. It does not publish to Central or the Gradle Plugin Portal. The [independent consumer](../validation/models-consumer/README.md) verifies real models, Android/JVM packaging and iOS resource output. The [check workflow](../.github/workflows/models-check.yml) repeats artifact-based validation outside the checkout.

[Current local verification](../validation/default-models-verification.json) passed 19 plugin tests, automatic SDK/model setup in a relocated Central SDK consumer, native synthesis/transcription, both iOS links and the actual Xcode resource hook. The whole-bundle opt-out built offline with a fresh empty model cache and no generated model files. App-owned resources and configuration-cache reuse are covered by functional tests. The plugin JAR is 51,480 bytes and contains no weights. The workflow is prepared but has not executed remotely. [Earlier selection/compression evidence](../validation/optional-models-verification.json) remains historical.

## Recent Changes

Added independent Central publication with sources/Dokka docs, three release-guard regressions, artifact-size/no-weights checks and a guarded manual release workflow. The core SDK is not republished.

Made the quick-start supply the SDK and both models by default. Added a whole-bundle opt-out that preserves app-owned resources and a single SDK version setting. The add-on is unpublished.
