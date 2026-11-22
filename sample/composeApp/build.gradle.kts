import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
val prepareAssets = rootProject.tasks.named<PrepareAndroidSpeech>("prepareAndroidSpeech")
extensions.configure<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(prepareAssets, PrepareAndroidSpeech::assetsDirectory) }
}
kotlin {
    android {
        namespace = "io.github.debanshu.siren.sample.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
    }
    jvm()
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    val suffix = target.name.replaceFirstChar(Char::uppercaseChar)
    val prepareAudio = rootProject.tasks.named<PrepareIosAudio>("prepareIosAudio$suffix")
    val bundledDefinition = tasks.register<GenerateBundledCinteropDefinition>("bundleAudioCinterop$suffix") {
        dependsOn(prepareAudio)
        templateFile.set(layout.projectDirectory.file("src/iosMain/cinterop/audio.def"))
        staticArchives.from(prepareAudio.flatMap { it.archiveFile })
        outputDirectory.set(layout.buildDirectory.dir("generated/cinterop/audio/${target.name}"))
    }
    val interop = target.compilations.getByName("main").cinterops.create("audioNative") {
        definitionFile.set(layout.buildDirectory.file("generated/cinterop/audio/${target.name}/interop.def"))
        includeDirs.allHeaders(layout.projectDirectory.dir("src/iosMain/cinterop"))
    }
    tasks.named(interop.interopProcessingTaskName) {
        dependsOn(bundledDefinition)
        inputs.dir(bundledDefinition.flatMap { it.outputDirectory })
        inputs.dir(layout.projectDirectory.dir("src/iosMain/cinterop"))
    }
    target.binaries.framework {
        baseName = "SirenApp"
        isStatic = true
        binaryOption("bundleId", "io.github.debanshu.siren.sample.shared")
        // Native archive bytes come from SDK/audio cinterop KLIBs. These are system libraries only.
        linkerOpts("-lc++", "-framework", "Foundation", "-framework", "CoreML",
            "-framework", "CoreFoundation", "-framework", "Accelerate",
            "-framework", "AVFoundation", "-framework", "AudioToolbox")
        linkTaskProvider.configure { dependsOn(bundledDefinition) }
    }
}
    sourceSets {
        commonMain.dependencies {
            api(project(":siren"))
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha05")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
        }
        androidMain.dependencies { implementation("androidx.activity:activity-compose:1.13.0") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
tasks.matching { it.name.startsWith("link") && it.name.contains("Framework") }.configureEach {
    dependsOn(rootProject.tasks.named("prepareSpeechModels"))
}
