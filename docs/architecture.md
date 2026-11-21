# How the pieces fit

The shared Compose screen delegates to `SpeechController`, which owns a `SpeechPlatform` and serializes its use. The platform factory is injected by each host; `expect`/`actual`, a DI framework and a service locator are unnecessary here.

```text
Record -> platform microphone -> mono FloatArray + actual sample rate
       -> sherpa Whisper -> recognized text -> shared editable text
       -> sherpa Kitten -> mono FloatArray + generated sample rate
       -> platform playback
```

Android uses the official sherpa Kotlin wrappers from its prebuilt AAR. The AAR already contains native inference libraries; adding a separate ONNX Runtime AAR could introduce duplicate native libraries.

iOS uses Kotlin/Native cinterop with sherpa's C API from `SherpaOnnxC.xcframework`, plus its matching ONNX Runtime binary. A small Objective-C++ bridge handles AVFoundation capture and playback only. C structs are zero-initialized before required fields are set. Every recognizer, stream, result, TTS session, generated buffer and audio handle has explicit cleanup.

Microphone capture preserves the actual hardware sample rate. Android converts signed PCM16 to float by dividing by 32768. iOS mixes hardware float channels to mono. Both feed that rate to sherpa's `acceptWaveform` API, which performs the model-rate conversion. Changing just the sample-rate integer would change the meaning of the audio. Synthesis is played at its returned rate.

The controller's ownership mutex remains held while native work runs. A canceled coroutine cannot destroy resources used by that work. Platform code checks cancellation after synchronous decode/generation before returning results. Stop and close acquire ownership before teardown. An independent UI-owned scope allows disposal cleanup to finish before the scope cancels itself.

The state paths are `Idle -> Starting -> Recording -> Transcribing -> Idle` and `Idle -> Synthesizing -> Playing -> Idle`. Failures return to idle with an error. Lifecycle `ON_STOP` cancels work and releases audio; disposal closes the platform. A permission request can finish after cancellation, but its stale result cannot start recording.

See the root README for deliberate sample limits. This is a focused foreground teaching app, not a background dictation service.
