# iOS host

Open `iosApp.xcodeproj`, select the OfflineSpeech scheme and an iOS 18.5+ arm64 simulator on an Apple Silicon Mac. Gradle automatically prepares pinned models, frameworks and the target audio bridge. Optional explicit setup from the root is `./gradlew prepareIosSpeech prepareIosAudioIosSimulatorArm64`. Physical devices require your own signing team.

The SwiftUI host links the shared SpeechApp framework and bundles the generated root `models` directory. The build phase invokes Gradle to produce the framework. See [iOS notes](../docs/ios-notes.md) and [verification](../docs/development.md).

Shared Kotlin code uses `com.debanshu777.speech`, with C bindings in `.native`. Swift continues to import `SpeechApp` and call `MainViewControllerKt.MainViewController()`. The host bundle ID remains `dev.debanshu.offlinespeech` to preserve its installation identity.

Full simulator and unsigned device builds were checked; the app was launched on
an iPhone 17 arm64 simulator. The separate [native C API result](../docs/ios-native-smoke.json)
verifies a Kitten-to-Whisper round trip. Microphone capture and audible output
remain manual checks; see [the actual UI capture](../docs/demo.md).

## Recent Changes

Reconciled the host documentation with the shared Kotlin and C interop package rename to `com.debanshu777.speech`; the Swift framework entry point and host bundle ID remain stable.
