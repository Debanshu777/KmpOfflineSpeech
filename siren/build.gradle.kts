import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

group = "io.github.debanshu777"
version = providers.gradleProperty("sirenVersion").get()

val prepareRuntime = rootProject.tasks.named<PrepareAndroidRuntime>("prepareAndroidRuntime")
val prepareJvmRuntime = rootProject.tasks.named<PrepareJvmRuntime>("prepareJvmRuntime")
extensions.configure<com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareRuntime, PrepareAndroidRuntime::jniLibsDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(prepareRuntime, PrepareAndroidRuntime::noticesDirectory)
    }
}

kotlin {
    explicitApi()
    android {
        namespace = "io.github.debanshu.siren"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }
    }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0") }
        androidMain.dependencies {
            implementation(files(prepareRuntime.flatMap { it.classesJar }).builtBy(prepareRuntime))
        }
        jvmMain {
            resources.srcDir(prepareJvmRuntime.flatMap { it.resourcesDirectory })
            dependencies { implementation(files(prepareJvmRuntime.flatMap { it.wrapperJar }).builtBy(prepareJvmRuntime)) }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

tasks.named<Jar>("jvmJar") {
    dependsOn(prepareJvmRuntime)
    from(prepareJvmRuntime.flatMap { it.embeddedClassesDirectory })
}

tasks.withType<Test>().configureEach {
    systemProperty("siren.native", providers.gradleProperty("native").getOrElse("false"))
    providers.gradleProperty("modelsDir").orNull?.let { systemProperty("siren.modelsDir", it) }
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-repository"))
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Siren")
            description.set("UI-free on-device speech recognition and synthesis for Kotlin Multiplatform")
            url.set("https://github.com/Debanshu777/Siren")
            licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            scm { url.set("https://github.com/Debanshu777/Siren") }
        }
    }
}

// Static native payloads travel with the SDK cinterop KLIBs.
kotlin {
listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
    val suffix = target.name.replaceFirstChar(Char::uppercaseChar)
    val slice = if (target.name == "iosArm64") "ios-arm64" else "ios-arm64_x86_64-simulator"
    val prepareRuntime = rootProject.tasks.named<PrepareIosSpeech>("prepareIosSpeech")
    val sherpaSlice = prepareRuntime.flatMap { it.sherpaFramework.dir(slice) }
    val onnxSlice = prepareRuntime.flatMap { it.onnxFramework.dir(slice) }
    val sherpaHeaders = rootProject.layout.projectDirectory.dir("libs/ios/SherpaOnnxC.xcframework/$slice/SherpaOnnxC.framework/Headers")
    val bundledDefinition = tasks.register<GenerateBundledCinteropDefinition>("bundleSherpaCinterop$suffix") {
        dependsOn(prepareRuntime)
        templateFile.set(layout.projectDirectory.file("src/iosMain/cinterop/sherpa.def"))
        staticArchives.from(
            sherpaSlice.map { it.file("SherpaOnnxC.framework/SherpaOnnxC") },
            onnxSlice.map { it.file("onnxruntime.framework/onnxruntime") },
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/cinterop/sherpa/${target.name}"))
    }
    val interop = target.compilations.getByName("main").cinterops.create("sherpaNative") {
        definitionFile.set(layout.buildDirectory.file("generated/cinterop/sherpa/${target.name}/interop.def"))
        includeDirs.allHeaders(sherpaHeaders)
    }
    tasks.named(interop.interopProcessingTaskName) {
        dependsOn(bundledDefinition)
        inputs.dir(bundledDefinition.flatMap { it.outputDirectory })
        inputs.dir(sherpaHeaders)
    }
}
}
