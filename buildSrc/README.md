# Speech build tools

Typed Gradle tasks prepare SDK runtimes separately from sample weights and hardware audio. `scripts/dependencies.json` records pinned artifacts and notices. Task actions perform preparation; Gradle configuration does not download artifacts or inspect generated headers.

| Task | Output and ownership |
| --- | --- |
| `prepareSpeechModels` | Root `models/`, including Kitten notices; sample-only weights |
| `prepareAndroidRuntime` | `siren/build/generated/androidRuntime/`: verified upstream AAR, wrapper JAR, two ABI JNI payloads and runtime notices; no weights |
| `prepareJvmRuntime` | `siren/build/generated/jvmRuntime/`: official wrapper JAR, embedded classes and six desktop native resource sets/notices; no weights |
| `prepareAndroidSpeech` | `sample/composeApp/build/generated/speechAssets/models`; depends on prepared sample weights |
| `prepareIosSpeech` | Pinned sherpa/ONNX XCFrameworks in `libs/ios/`; no weight dependency |
| `prepareIosAudioIosArm64` | Device `libs/ios/audio/iosArm64/libspeech_audio.a`; sample AVFoundation bridge |
| `prepareIosAudioIosSimulatorArm64` | Simulator `libs/ios/audio/iosSimulatorArm64/libspeech_audio.a` |
| `:siren:bundleSherpaCinterop...` | Generated sherpa definition/archive directory for each SDK iOS target |
| `:sample:composeApp:bundleAudioCinterop...` | Generated sample audio definition/archive directory for each iOS target |

Android SDK generated providers carry wrapper/JNI/notices dependencies; sample generated assets carry their separate model dependency. Native cinterop processing depends on its generated definition. `GenerateBundledCinteropDefinition` copies static archives and writes deterministic `staticLibraries`/`libraryPaths` properties, embedding native bytes in KLIBs. The sample Xcode host then links its Kotlin framework and system libraries without raw native checkout paths.

`prepareAndroidRuntime` checks the expected library set for arm64-v8a and x86_64 and preserves upstream notices plus pinned manifest notice entries. The wrapper JAR and selected JNI bytes are copied without modification. The downloaded AAR is a build input/provenance artifact; consumers receive runtime content in the SDK artifact. Consumer JNI keep rules live in `siren/consumer-rules.pro`.

`prepareJvmRuntime` verifies the Java wrapper plus six official native JARs, checks two native libraries per classifier, and stages `sherpa-onnx/native/{osx-aarch64,osx-x64,linux-aarch64,linux-x64,win-arm64,win-x64}` resources. SDK `jvmJar` embeds the Java class files and resources, so publication does not depend on a checkout-local wrapper file. Platform notices and provenance travel with the runtime. Packaged/hash-verified targets are not automatically execution-verified.

Downloads use `.cache/downloads` as local state. Cached and new files must match SHA-256 pins and optional sizes. Verified temporary files are published only after validation. ZIP/tar.bz2 extraction rejects traversal, links, special files, duplicates, file/directory conflicts and expansion beyond 3 GiB. Extracted Kitten files are individually verified; notices are retained. Output trees are staged before replacement.

## Checks and offline use

```bash
./gradlew checkSpeechSetup
./gradlew -p buildSrc test
```

These run the build-tool regressions. `checkSpeechSetup` invokes the special `buildSrc` build through `GradleBuild`; `buildSrc` is not a root subproject.

Use `--offline` after the selected Gradle dependencies and artifacts have been cached. Runtime/model tasks declare manifest inputs and outputs and can use Gradle's build cache. Apple compilation declares source/header, target, deployment minimum and Xcode identity and is tracked locally. Generated cinterop definitions contain local archive search directories and are not shared through the build cache; their compiled KLIBs include the native archive bytes.

SDK-only preparation does not obtain model weights. Default sample builds do obtain both demonstration bundles. Runtime-null configurations do not change those build outputs.

The desktop sample's `:sample:desktopApp:stageDesktopModels` task uses `prepareSpeechModels` to build classpath `models/` resources. It is separate from `prepareJvmRuntime`; only the sample JAR/application includes those weights.

## Recent Changes

Added portable JVM wrapper/native preparation independently from sample models, retaining verified Android/iOS payloads and deterministic cinterop archive bundling.
