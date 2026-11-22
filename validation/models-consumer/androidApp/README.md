# Android model-packaging consumer

This launcher consumes the fixture's shared KMP module, which exports the public
Siren 0.1.0 coordinate and automatically generated model assets. It has no Compose,
sample-module or checkout-native dependency. The build checks default and explicit
SDK factories, transitive JNI packaging and model files in the actual APK. It does
not request microphone permission or run Android inference.
See the [fixture recipe](../README.md).

## Recent Changes

Added optional model assets through the shared module and hash-based APK inspection. Minimum API remains 27.
