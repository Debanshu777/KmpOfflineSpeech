# iOS host

Run `python3 scripts/setup.py --platform ios` and `python3 scripts/prepare_ios.py` from the root, then open `iosApp.xcodeproj`. Select the OfflineSpeech scheme and an iOS 18.5+ simulator. Physical devices require your own signing team.

The SwiftUI host links the shared SpeechApp framework and bundles the generated root `models` directory. The build phase invokes Gradle to produce the framework. See [iOS notes](../docs/ios-notes.md) and [verification](../docs/development.md).

Full simulator and unsigned device builds were checked; the app was launched on
an iPhone 17 arm64 simulator. The separate [native C API result](../docs/ios-native-smoke.json)
verifies a Kitten-to-Whisper round trip. Microphone capture and audible output
remain manual checks; see [the actual UI capture](../docs/demo.md).

## Recent Changes

Added the SwiftUI launcher, model resources, required Compose plist settings and shared framework build phase, with simulator launch and native model evidence.
