# Siren guides

Start with the [model integration tutorial](medium-draft.md), which assumes an existing KMP project, or run the completed sample using the [quick setup](../README.md). The [demo](demo.md) shows the focused recorder design and a real Android recording → transcript → playback GIF.

- [Architecture and deliberate limits](architecture.md)
- [Configuring and replacing speech models](model-configuration.md)
- [SDK and sample architecture](library-design.md)
- [Development and verification](development.md)
- [iOS native integration and smoke command](ios-notes.md)
- [Actual app capture and demo notes](demo.md)
- [Third-party components and licenses](../THIRD_PARTY_NOTICES.md)

The UI-free `siren` SDK owns configurable speech inference. The separate Android and iOS sample shares Kotlin interaction logic and Compose UI. Whisper and Kitten execute locally through sherpa-onnx; generated weights and runtimes stay outside Git.

Source packages use `io.github.debanshu.siren`; see the [package map and installation identities](architecture.md#packages-and-installation-identities).
