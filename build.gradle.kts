plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("com.android.kotlin.multiplatform.library") version "9.5.1" apply false
    id("com.android.application") version "9.5.1" apply false
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
tasks.register<PrepareAndroidSpeech>("prepareAndroidSpeech") {
    group = "speech setup"
    description = "Prepare the official speech AAR and generated Android model assets"
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    modelDirectory.set(prepareSpeechModels.flatMap { it.modelDirectory })
    runtimeAar.set(layout.projectDirectory.file("composeApp/libs/sherpa-onnx-2.0.1.aar"))
    assetsDirectory.set(layout.projectDirectory.dir("composeApp/build/generated/speechAssets"))
}
val prepareIosSpeech = tasks.register<PrepareIosSpeech>("prepareIosSpeech") {
    group = "speech setup"
    description = "Prepare pinned Apple speech frameworks and models"
    dependsOn(prepareSpeechModels)
    manifestFile.set(speechManifest)
    downloadCache.set(speechCache)
    offline.set(gradle.startParameter.isOffline)
    sherpaFramework.set(layout.projectDirectory.dir("libs/ios/SherpaOnnxC.xcframework"))
    onnxFramework.set(layout.projectDirectory.dir("libs/ios/onnxruntime.xcframework"))
}
listOf("IosArm64" to "iosArm64", "IosSimulatorArm64" to "iosSimulatorArm64").forEach { (suffix, targetName) ->
    tasks.register<PrepareIosAudio>("prepareIosAudio$suffix") {
        group = "speech setup"
        description = "Compile the AVFoundation audio bridge for $targetName"
        dependsOn(prepareIosSpeech)
        target.set(targetName)
        deploymentTarget.set("18.5")
        toolchainIdentity.set(providers.exec { commandLine("xcodebuild", "-version") }.standardOutput.asText)
        bridgeDirectory.set(layout.projectDirectory.dir("composeApp/src/iosMain/cinterop"))
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
