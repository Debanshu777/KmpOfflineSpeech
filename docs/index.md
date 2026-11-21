# Offline speech example guides

Start with the [model integration tutorial](medium-draft.md), which assumes an existing KMP project, or run the completed sample using the [quick setup](../README.md). The [demo](demo.md) shows the focused recorder design and a real Android recording → transcript → playback GIF.

- [Architecture and deliberate limits](architecture.md)
- [Development and verification](development.md)
- [iOS native integration and smoke command](ios-notes.md)
- [Actual app capture and demo notes](demo.md)
- [Third-party components and licenses](../THIRD_PARTY_NOTICES.md)

The Android and iOS apps share Kotlin interaction logic and UI. Whisper and Kitten execute locally through sherpa-onnx; generated weights and runtimes stay outside Git.

Source packages use `com.debanshu777.speech`; see the [package map and installation identities](architecture.md#packages-and-installation-identities).
