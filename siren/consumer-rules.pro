# sherpa-onnx JNI resolves wrapper classes, configuration fields, and method descriptors by name.
# Keep these upstream names when an application shrinks a dependency on the Siren SDK.
-keep class com.k2fsa.sherpa.onnx.** { *; }
