# Plain Android artifact consumer

This launcher uses the staged Siren Maven coordinate directly. It has no Compose,
sample-module or local AAR dependency. Its build checks the default
`createSpeechEngine(context)` factory and the original explicit-model factory
and transitive JNI packaging; it does not run inference or request permissions.
See the [fixture recipe](../README.md).

## Recent Changes

The plain consumer APK targets minimum API 27 and compiles the standard SDK defaults alongside legacy factory coverage.
