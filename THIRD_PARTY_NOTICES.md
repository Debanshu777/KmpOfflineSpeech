# Third-party components

This repository contains sample source under MIT. Runtime binaries and model weights are downloaded by Gradle preparation tasks and are not covered by the sample's MIT license.

| Component | Upstream license/source |
| --- | --- |
| sherpa-onnx 2.0.1 | [Apache-2.0 license](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/LICENSE) |
| ONNX Runtime | [MIT license](https://github.com/microsoft/onnxruntime/blob/main/LICENSE), packaged by the [pinned upstream Swift package](https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/Package.swift) |
| Whisper Tiny weights | [OpenAI Whisper MIT license](https://github.com/openai/whisper/blob/main/LICENSE), [ONNX conversion source](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c) |
| Kitten nano 0.8 int8 weights | [KittenML model card, Apache-2.0](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8), [sherpa packaging](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) |
| Pronunciation lexicon | [sherpa TTS asset release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models); retain accompanying upstream data notices when redistributing |
| Kotlin, Compose and AndroidX | [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt), and [AndroidX](https://developer.android.com/jetpack/androidx) upstream notices |

The prebuilt runtime bundles include other third-party components and notices. Preserve notices supplied by their releases when distributing binaries. For the exact artifact URLs, SHA-256 hashes and model revision, see `scripts/dependencies.json`. The setup script retains any supplied model notices in the extracted model directory; it does not relicense them.
