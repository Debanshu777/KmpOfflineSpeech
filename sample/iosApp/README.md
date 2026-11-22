# Siren iOS sample host

Open `sample/iosApp/iosApp.xcodeproj` from the repository root, choose the **Siren** scheme and an iOS 18.5+ arm64 simulator on an Apple Silicon Mac. Physical devices need a signing team. The pinned Compose host's ICU dependency sets this baseline; it is not a general speech/KMP minimum.

The build phase changes to the repository root and runs `:sample:composeApp:embedAndSignAppleFrameworkForXcode`. It prepares selected native inputs and sample weights automatically. Swift imports `SirenApp` and calls `MainViewControllerKt.MainViewController()`. The host installation ID remains `dev.debanshu.offlinespeech`.

The host links generated `SirenApp.framework` plus Apple system libraries. SDK sherpa/ONNX static bytes and the sample AVFoundation archive are embedded through separate cinterop KLIBs. There are no raw sherpa/ONNX checkout framework search paths in this host. Bundled model files come from root `models`, copied as a folder resource.

`IosSpeechPlatform(engine)` provides hardware audio and permissions. The sample explicitly creates its engine with `createIosSpeechEngine(SampleModels.bundledDemo())`; SDK users can configure their own files or providers without this host. SDK C bindings use `io.github.debanshu.siren.internal.native`; sample audio uses `.sample.internal.audio`.

See [iOS notes](../../docs/ios-notes.md), [configuration](../../docs/model-configuration.md) and [verification](../../docs/development.md). The moved host built, installed and launched in the iPhone 17/iOS 27 simulator; the [current capture](../../docs/images/ios-siren-simulator.png) shows the real app. A separate [relocated artifact consumer](../../validation/published-consumer-verification.json) linked dynamic frameworks for both iOS arm64 targets without checkout-native paths. Neither result establishes physical iPhone microphone/speaker behavior. Historical native captures retain their original scope.

## Recent Changes

Verified the moved simulator host and relocated native consumer linkage, retaining sample-only audio/models and no raw native checkout search paths.
