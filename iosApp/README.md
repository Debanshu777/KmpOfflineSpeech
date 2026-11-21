# iOS host

Run `python3 scripts/setup.py --platform ios` and `python3 scripts/prepare_ios.py` from the root, then open `iosApp.xcodeproj`. Select the OfflineSpeech scheme and an iOS 18.5+ simulator. Physical devices require your own signing team.

The SwiftUI host links the shared SpeechApp framework and bundles the generated root `models` directory. The build phase invokes Gradle to produce the framework. See [iOS notes](../docs/ios-notes.md) and [verification](../docs/development.md).

## Recent Changes

Added the SwiftUI launcher, model resource folder, microphone usage string and shared framework build phase.
