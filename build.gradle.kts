plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.1" apply false
    id("com.android.application") version "9.5.1" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
}

val speechManifest = layout.projectDirectory.file("scripts/dependencies.json")
val speechCache = layout.projectDirectory.dir(".cache/downloads")
val prepareSpeechModels = tasks.register<PrepareSpeechModels>("prepareSpeechModels") {
    group = "speech setup"
    description = "Download and verify Whisper/Kitten models and bundled notices"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    modelDirectory.set(layout.projectDirectory.dir("models"))
}
tasks.register<PrepareAndroidRuntime>("prepareAndroidRuntime") {
    group = "speech setup"
    description = "Prepare the SDK's pinned wrapper JAR, supported JNI libraries and runtime notices"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    runtimeAar.set(layout.projectDirectory.file("siren/build/generated/androidRuntime/sherpa-onnx-2.0.1.aar"))
    classesJar.set(layout.projectDirectory.file("siren/build/generated/androidRuntime/sherpa-onnx-classes.jar"))
    jniLibsDirectory.set(layout.projectDirectory.dir("siren/build/generated/androidRuntime/jniLibs"))
    noticesDirectory.set(layout.projectDirectory.dir("siren/build/generated/androidRuntime/notices"))
}
tasks.register<PrepareJvmRuntime>("prepareJvmRuntime") {
    group = "speech setup"
    description = "Prepare the portable SDK JVM wrapper and six pinned desktop native resource sets"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    wrapperJar.set(layout.projectDirectory.file("siren/build/generated/jvmRuntime/sherpa-onnx-jvm-2.0.1.jar"))
    embeddedClassesDirectory.set(layout.projectDirectory.dir("siren/build/generated/jvmRuntime/classes"))
    resourcesDirectory.set(layout.projectDirectory.dir("siren/build/generated/jvmRuntime/resources"))
}
tasks.register<PrepareAndroidSpeech>("prepareAndroidSpeech") {
    group = "speech setup"
    description = "Prepare sample-only Android model assets"
    modelDirectory.set(prepareSpeechModels.flatMap { it.modelDirectory })
    assetsDirectory.set(layout.projectDirectory.dir("sample/composeApp/build/generated/speechAssets"))
}
val prepareIosSpeech = tasks.register<PrepareIosSpeech>("prepareIosSpeech") {
    group = "speech setup"
    description = "Prepare pinned SDK Apple speech runtime frameworks"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    sherpaFramework.set(layout.projectDirectory.dir("libs/ios/SherpaOnnxC.xcframework"))
    onnxFramework.set(layout.projectDirectory.dir("libs/ios/onnxruntime.xcframework"))
    noticesDirectory.set(layout.projectDirectory.dir("siren/build/generated/iosRuntime/notices"))
}
listOf("IosArm64" to "iosArm64", "IosSimulatorArm64" to "iosSimulatorArm64").forEach { (suffix, targetName) ->
    tasks.register<PrepareIosAudio>("prepareIosAudio$suffix") {
        group = "speech setup"
        description = "Compile the AVFoundation audio bridge for $targetName"
        dependsOn(prepareIosSpeech)
        target.set(targetName)
        deploymentTarget.set("18.5")
        toolchainIdentity.set(providers.exec { commandLine("xcodebuild", "-version") }.standardOutput.asText)
        bridgeDirectory.set(layout.projectDirectory.dir("sample/composeApp/src/iosMain/cinterop"))
        val slice = if (targetName == "iosArm64") "ios-arm64" else "ios-arm64_x86_64-simulator"
        sherpaHeaders.set(prepareIosSpeech.flatMap { it.sherpaFramework.dir("$slice/SherpaOnnxC.framework/Headers") })
        archiveFile.set(layout.projectDirectory.file("libs/ios/audio/$targetName/libspeech_audio.a"))
    }
}
tasks.register<GradleBuild>("checkSpeechSetup") {
    group = "verification"
    description = "Run checksum, cache and safe extraction tests in the buildSrc build"
    dir = file("buildSrc")
    tasks = listOf("test")
    buildName = "speechSetupVerification"
}

tasks.register<VerifyNativeRedistribution>("verifyNativeRedistribution") {
    group = "publishing"
    description = "Verify pinned native redistribution notices and complete source coverage before Central upload"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    reportFile.set(layout.buildDirectory.file("reports/native-redistribution.json"))
    compilerEvidenceFile.set(layout.projectDirectory.file("scripts/native-compiler-evidence.json"))
}

apply(from = "gradle/release.gradle.kts")
