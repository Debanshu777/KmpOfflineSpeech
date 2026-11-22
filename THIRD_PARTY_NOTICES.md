# Third-party components and redistribution status

Siren's first-party SDK and sample source is MIT licensed. Downloaded native runtimes and model weights keep their upstream licenses. The SDK does not contain model weights or Compose UI. Listing a component here does not relicense it or certify an incomplete binary audit.

**Public native binary release is currently blocked.** `./gradlew verifyNativeRedistribution` writes `build/reports/native-redistribution.json` and fails until every pinned runtime payload and component has a complete review, immutable source identity, and verified accompanying notices. Ordinary local builds remain available. There is no release-gate bypass property.

## Native runtime inventory

| Distributed payload | Upstream and observed source identity | Review |
| --- | --- | --- |
| Android wrapper/JNI, arm64-v8a and x86_64 | [sherpa-onnx 2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1); embedded ONNX Runtime build information reports commit `33ca962` | Incomplete |
| JVM Java wrapper and Linux, macOS, Windows native payloads, each arm64/x64 | Same sherpa release. macOS and Windows ONNX Runtime binaries report `33ca962`; both Linux binaries leave the branch and commit fields empty | Incomplete |
| iOS device and simulator static Sherpa archives | [Pinned sherpa Swift manifest](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/Package.swift) | Incomplete |
| iOS device and simulator static ONNX Runtime archives | [Vendor Swift manifest v1.28.2](https://github.com/csukuangfj/onnxruntime-libs/blob/v1.28.2/Package.swift) deliberately references the 1.28.1 binary. Both archives report commit `5181af9` | Incomplete |

The [official Microsoft release listing](https://github.com/microsoft/onnxruntime/releases) associates v1.28.2 with `33ca962` and v1.28.1 with `5181af9`. Microsoft commit/tag/raw requests and public Git queries failed during this audit. The binary vendor’s source fork subsequently resolved both exact commits: [1.28.2](https://github.com/csukuangfj/onnxruntime/commit/33ca9628233dc8f002435e868d4c2e9f82766ca1) and [1.28.1](https://github.com/csukuangfj/onnxruntime/commit/5181af9bef60f46194ec30506855d74b6e6a96ff). Their Microsoft MIT `LICENSE` and full `ThirdPartyNotices.txt` were retrieved at those full commit URLs and checksum-pinned. The two versions contain identical license/notice bytes; both source references are retained. No current-main or different-version notice was substituted. This resolves the notice retrieval concern recorded in [upstream issue 4262](https://github.com/k2-fsa/sherpa-onnx/issues/4262) for these observed source commits, while the wider binary review below remains open.

All ten observed native variants, archive checksums, original build-info strings, audit status, source links and notice checksums are recorded in [scripts/dependencies.json](scripts/dependencies.json). For the two Linux variants, the original upstream ZIPs were also fetched at Sherpa’s CMake SHA-256 pins. Each contains the full `GIT_COMMIT_ID` above, and its `libonnxruntime.so` byte-for-byte SHA-256 matches the library inside the packaged JNI JAR. Their included license and notice files match the full-commit source files. This closes ONNX Runtime source/notice correspondence for all packaged variants. It does not complete the separate Sherpa transitive/compiler-runtime review.

## Verified notice texts retained so far

Preparation tasks verify and retain these 21 text files from pinned upstream source URLs:

| Component and source version | Retained texts |
| --- | --- |
| ONNX Runtime 1.28.2 and 1.28.1, exact source commits above | Each version’s Microsoft MIT `LICENSE` and full `ThirdPartyNotices.txt` |
| sherpa-onnx 2.0.1 | Apache-2.0 `LICENSE`; the inspected pinned source has no top-level `NOTICE` |
| kaldi-native-fbank 1.22.4; kaldi-decoder 0.3.0 | Each component's `LICENSE` |
| kaldifst 1.8.0 | `LICENSE`, including its retained attribution |
| simple-sentencepiece 0.7 | `LICENSE` |
| utf8proc 2.12.0 | `LICENSE.md`, including Unicode terms and attribution |
| nlohmann/json 3.12.0 | `LICENSE.MIT` |
| KissFFT commit `febd4caeed32e33ad8b2e0bb5ea77542c40f18ec` | `COPYING` and the referenced `LICENSES/BSD-3-Clause` |
| OpenFst 1.8.5-2026-07-09 | `COPYING`, retaining Google attribution; the Apache-2.0 text is also included |
| hclust-cpp 2026-02-25 | `LICENSE`, retaining the authors' BSD terms |
| Eigen 5.0.1 | `LICENSE`, `COPYING.README`, `COPYING.MPL2`, `COPYING.BSD`, `COPYING.MINPACK`, `COPYING.APACHE` |

These are a conservative set derived from the [pinned Sherpa CMake dependency definitions](https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.1/cmake). Their inclusion is not a claim that every listed optional component is in every binary. The exact official build flags, full native dependency inventory, all supplied attribution/notice files, and any applicable source-availability obligations (including MPL-covered files) remain to be reconciled. The exact-version ONNX Runtime license and notice files are now pinned; the remaining release blocker is the full official Sherpa binary/transitive inventory and applicable source obligations, including compiler runtime attribution.

The bounded follow-up inspection is recorded in [scripts/native-redistribution-evidence.json](scripts/native-redistribution-evidence.json), with checksums and import tables for twenty Android/JVM libraries. Android ELF notes identify the Sherpa binaries as NDK r30 build 16248370 and ONNX Runtime as NDK r27d build 13750724. Their import tables omit `libc++_shared.so`; the upstream scripts and Android's documented CMake default indicate static libc++. Exact r27d notice/version files were inspected through small range reads of the official NDK archive; r30 notice files were identified in the matching installed SDK. These compiler-runtime notices are identified but have not yet been added to the publication inventory. Windows JNI binaries use differing PE linker versions (x64:14.44; arm64:14.51), import no separate MSVCP/VCRUNTIME DLL, and the upstream configuration defaults to static CRT. Their exact compiler-runtime notice provenance remains unresolved. Linux JNI imports system `libstdc++.so.6`/`libgcc_s.so.1`; macOS libraries import the system libc++.

[Eigen's source-availability statement](scripts/notices/SOURCE-AVAILABILITY.txt) provides the public unmodified 5.0.1 source URL and SHA-256. The archive was downloaded and matched the exact upstream CMake pin. The same statement and its text checksum are recorded in `scripts/dependencies.json` for artifact embedding. The source scan found no Eigen patch step in Sherpa's configuration. This supplies a concrete MPL source location without marking the wider binary audit complete.

## Where notices travel

- Android AAR: `assets/META-INF/siren/` contains the pinned license files, runtime provenance and explicit audit metadata.
- JVM JAR: `META-INF/siren/` contains the same license inventory and audit metadata, plus desktop runtime provenance.
- iOS native cinterop KLIB: `default/resources/META-INF/siren/` contains the notice inventory alongside the embedded static archive payload. KLIB notice embedding preserves native archive and compiler metadata bytes.

Preparation also retains any license, notice, copying or copyright files supplied inside the original runtime archives. The inspected Android/JVM archives did not supply a complete native notice bundle. iOS preparation scans the entire upstream ZIP, including files outside the XCFramework directory. Downstream distributions must retain applicable notices and satisfy their license terms; applications should expose the relevant notices in their distributed materials.

## Sample-only models and other dependencies

The sample downloads weights separately from SDK publication. [Whisper](https://github.com/openai/whisper/blob/main/LICENSE) uses MIT; its ONNX conversion is locked to the [model repository revision](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c). [Kitten nano 0.8 int8](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8) is described by its model card as Apache-2.0. The [Sherpa TTS asset release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) supplies the conversion and pronunciation lexicon; preserve accompanying data notices when redistributing. Sample model preparation retains supplied license files, and model review is separate from the native SDK release gate.

Kotlin, coroutines, atomicfu, Compose, AndroidX and test/build dependencies retain their own licenses and Maven metadata. Compose and application audio hosts belong to the sample, not the speech SDK. See the dependency declarations and upstream license files for these separately resolved dependencies.
