# Optional model add-on consumer

This separate KMP app fixture resolves the **published Siren 0.1.0 SDK** from Maven Central and obtains its model files through the optional build-time plugin. It contains no manually copied model resources, explicit SDK dependency declaration or source-project SDK dependency; the quick-start plugin supplies both dependency and resources.

By default it includes the plugin build from this checkout. Set `modelPluginRepository` to a staged Maven repository to resolve the plugin implementation and marker as artifacts instead; that mode can run after copying the fixture outside the checkout.

From the Siren root:

```bash
./gradlew -p validation/models-consumer jvmTest verifyModelPackaging \
  linkDebugFrameworkIosArm64 linkDebugFrameworkIosSimulatorArm64 -Pnative=true
```

Android SDK 37 and JDK 21 are required; iOS linking additionally requires macOS/Xcode. This downloads the selected model weights during the build. Use `-PmodelCache=/absolute/path/to/cache` to reuse a cache created by Siren's earlier preparation tasks. `--offline` works after the plugin, SDK, build dependencies and selected model files have been cached.

`verifyModelPackaging` hashes all selected model files inside the actual Android APK and JVM JAR and in the generated iOS resource directory, preserving model notices and rejecting stale unselected folders. The JVM native test runs synthesis and transcription through the published engine using the generated classpath resources. iOS framework linking is separate from resource-copy validation; neither alone verifies physical microphone/playback.

For a whole-bundle opt-out, use `:clean :androidApp:clean verifyModelPackaging -PbundleModels=false` with a fresh empty model cache and `--offline`. This checks that no generated weights remain and no download is needed. The SDK is still supplied by the plugin.

For a synthesis-only packaging check, use `-Precognition=false -Pnative=false`; the shared native roundtrip test needs both models. The report is `build/reports/model-packaging.json`.

Use `:androidApp:clean verifyModelPackaging -Precognition=false` when comparing APK sizes after changing selection: AGP's incremental debug ZIP writer can retain unused archive space even after removing entries. A fresh APK measures the selected files without those gaps.

The plugin's [functional tests](../../siren-models-gradle-plugin/src/test/kotlin/io/github/debanshu/siren/gradle/SirenModelsPluginTest.kt) verify Xcode destination handling, preservation of other app resources, configuration-cache reuse and failed-download behavior. [CI](../../.github/workflows/models-check.yml) stages and relocates the plugin repository before consuming it, with no publishing credentials.

[Current default/opt-out results](../default-models-verification.json) cover automatic SDK dependency setup, both models and a no-model-cache opt-out. [Earlier recorded results](../optional-models-verification.json) include synthesis-only selection and compression outputs. Model entries alone compressed to 90,608,967 bytes in the Android APK and 85,709,167 bytes in the JVM JAR, from 137,564,418 raw bytes. These are fixture archive measurements, excluding the engine and general app overhead; they do not measure installed size or RAM. The actual Xcode embed task also passed in a build-directory app fixture. No new installed-app audio check is claimed. The later [release workflow](https://github.com/Debanshu777/Siren/actions/runs/45073444409) also verified a fresh Central-only plugin consumer; see [publication evidence](../models-publication-0.1.0.json).

## Recent Changes

Added exclusive Central-only plugin-ID resolution with `-PmodelPluginFromCentral=true`; the release workflow validates the public plugin and SDK without checkout source substitution.
