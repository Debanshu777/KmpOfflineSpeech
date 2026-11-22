# Plain Android artifact consumer

This launcher uses the staged Siren Maven coordinate directly. It has no Compose,
sample-module or local AAR dependency. Its build checks the default
`createSpeechEngine(context)` factory and the original explicit-model factory
and transitive JNI packaging; it does not run inference or request permissions.
See the [fixture recipe](../README.md).

## Recent Changes

Compiled the minimal Android factory alongside the original configurable factory,
retaining the plain APK check for transitive native payload packaging.
