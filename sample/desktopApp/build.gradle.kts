import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val modelResources = layout.buildDirectory.dir("generated/modelResources")
val prepareModels = rootProject.tasks.named<PrepareSpeechModels>("prepareSpeechModels")
val stageDesktopModels = tasks.register<Sync>("stageDesktopModels") {
    dependsOn(prepareModels)
    from(prepareModels.flatMap { it.modelDirectory }) { into("models") }
    into(modelResources)
}

kotlin {
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    sourceSets {
        jvmMain {
            resources.srcDir(modelResources)
            dependencies {
                implementation(project(":sample:composeApp"))
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            }
        }
    }
}
tasks.named("jvmProcessResources") { dependsOn(stageDesktopModels) }

tasks.register<JavaExec>("nativeSpeechSmoke") {
    group = "verification"
    description = "Exercise the desktop's classpath models and SDK runtime without opening audio devices"
    val main = kotlin.targets.getByName<KotlinJvmTarget>("jvm").compilations.getByName("main")
    dependsOn("jvmJar")
    mainClass.set("io.github.debanshu.siren.sample.desktop.NativeSpeechSmokeKt")
    classpath = files(main.output.allOutputs, configurations.getByName("jvmRuntimeClasspath"))
}

compose.desktop {
    application {
        mainClass = "io.github.debanshu.siren.sample.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Siren"
            packageVersion = "1.0.0" // macOS jpackage requires a positive major version.
            modules("jdk.unsupported")
            linux { packageName = "siren" }
            macOS {
                bundleID = "io.github.debanshu.siren.sample.desktop"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Record a short clip for speech recognition on this device.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
