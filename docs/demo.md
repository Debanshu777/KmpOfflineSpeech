# Demo and captures

The foreground demo is: record up to 20 seconds, stop and transcribe, edit the resulting text, then Speak. Cancel/stop discards pending work; synchronous model execution may need to finish before teardown.

The actual simulator screenshot below documents the shared screen. It is not evidence of physical microphone capture, recognition accuracy or audible output.

![Offline Speech running on an iOS simulator](images/ios-simulator.png)

Capture notes: iPhone 17 arm64 simulator on the installed iOS 27 runtime, development build, 2026-11-21. Use `xcrun simctl io booted screenshot PATH` after installing/launching the current app, then inspect the image. Keep captures free of unrelated apps, notifications and private information.

For repeatable verification and evidence, see [development](development.md) and the [Android native result](../androidApp/verification/native-smoke-2026-11-21.json).
