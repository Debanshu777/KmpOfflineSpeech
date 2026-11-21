# Speech build tools

Small typed Gradle tasks prepare the sample's pinned runtimes, model files and
AVFoundation bridge. `scripts/dependencies.json` is their single artifact manifest.
Preparation runs at task execution time; Gradle configuration does not download
anything or inspect generated native headers.

| Root task | Output |
| --- | --- |
| `prepareSpeechModels` | `models/`, including bundled Kitten notices |
| `prepareAndroidSpeech` | Official AAR in `composeApp/libs/` and model assets in `composeApp/build/generated/speechAssets/` |
| `prepareIosSpeech` | Pinned XCFrameworks in `libs/ios/`; depends on models |
| `prepareIosAudioIosArm64` | Device `libs/ios/audio/iosArm64/libspeech_audio.a` |
| `prepareIosAudioIosSimulatorArm64` | Simulator `libs/ios/audio/iosSimulatorArm64/libspeech_audio.a` |

Android asset and AAR providers carry their preparation dependency. iOS cinterop
and framework linking depend on the matching audio task, which prepares iOS
runtime inputs first. Normal app builds therefore prepare what they need.

Downloads use `.cache/downloads/` as local state. Cache entries and new downloads
must match their SHA-256 pins and optional sizes. Only verified temporary files
are published. ZIP and tar.bz2 extraction reject traversal, links, special files,
duplicate paths, file/directory conflicts and expansion beyond 3 GiB. Model files
inside the Kitten archive are verified separately; bundled notices are retained.
Outputs are staged before publication, so failed preparation does not publish a
partial model tree.

`./gradlew checkSpeechSetup` runs the build-tool unit tests through a Gradle
`GradleBuild` task. Because `buildSrc` is a special build rather than a root
subproject, its test suite is invoked as its own Gradle build. For focused work,
`./gradlew -p buildSrc test` runs the same suite directly. No Python is needed.

Use `--offline` after Gradle dependencies and the selected speech artifacts have
been cached. Download/model preparation tasks declare their manifest inputs and
outputs and can use Gradle's build cache. Apple bridge compilation also declares
headers, source, target, deployment minimum and Xcode identity; its output is
tracked locally and is not shared through the build cache.

## Recent Changes

Replaced separate setup scripts with verified Gradle preparation, generated
Android assets, automatic native dependencies and extraction/cache regressions.
