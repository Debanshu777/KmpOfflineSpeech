# Plain Android artifact consumer

This launcher uses the staged Siren Maven coordinate directly. It has no Compose,
sample-module or local AAR dependency. Its build checks the public Android factory
and transitive JNI packaging; it does not run inference or request permissions.
See the [fixture recipe](../README.md).

## Recent Changes

Added a minimal APK consumer for artifact packaging verification.
