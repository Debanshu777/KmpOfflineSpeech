import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val prepareAndroidSpeech = rootProject.tasks.named<PrepareAndroidSpeech>("prepareAndroidSpeech")
extensions.configure<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareAndroidSpeech, PrepareAndroidSpeech::assetsDirectory)
    }
}

kotlin {
    android {
        namespace = "dev.debanshu.speech.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
    }
    jvm() // Runs common controller tests without a device or native libraries.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val slice = if (target.name == "iosArm64") "ios-arm64" else "ios-arm64_x86_64-simulator"
        val sherpa = rootProject.file("libs/ios/SherpaOnnxC.xcframework/$slice")
        val ort = rootProject.file("libs/ios/onnxruntime.xcframework/$slice")
        val audio = rootProject.file("libs/ios/audio/${target.name}")
        val prepareAudio = rootProject.tasks.named("prepareIosAudio${target.name.replaceFirstChar(Char::uppercaseChar)}")
        val interop = target.compilations.getByName("main").cinterops.create("speechNative") {
            defFile("src/iosMain/cinterop/speech.def")
            compilerOpts("-I${file("src/iosMain/cinterop")}", "-I${sherpa}/SherpaOnnxC.framework/Headers")
        }
        tasks.named(interop.interopProcessingTaskName) { dependsOn(prepareAudio) }
        target.binaries.framework {
            baseName = "SpeechApp"
            isStatic = true
            binaryOption("bundleId", "dev.debanshu.speech.shared")
            linkerOpts("-F$sherpa", "-framework", "SherpaOnnxC", "-F$ort", "-framework", "onnxruntime",
                "-L$audio", "-lspeech_audio", "-lc++", "-framework", "AVFoundation",
                "-framework", "AudioToolbox", "-framework", "Foundation", "-framework", "CoreML",
                "-framework", "CoreFoundation")
            linkTaskProvider.configure { dependsOn(prepareAudio) }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha05")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
        }
        androidMain.dependencies {
            implementation(files(prepareAndroidSpeech.flatMap { it.runtimeAar }).builtBy(prepareAndroidSpeech))
            implementation("androidx.activity:activity-compose:1.13.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
