import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.4.21"
    id("com.android.kotlin.multiplatform.library") version "9.5.1"
    id("com.android.application") version "9.5.1" apply false
}

val sirenVersion = providers.gradleProperty("sirenVersion").getOrElse("0.1.0")

kotlin {
    android {
        namespace = "io.github.debanshu.siren.validation.consumer"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SirenConsumer"
            isStatic = false // Force actual resolution of the SDK's native symbols.
            binaryOption("bundleId", "io.github.debanshu.siren.validation.consumer")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.debanshu777:siren:$sirenVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

val nativeProbe = providers.gradleProperty("native").map(String::toBoolean).getOrElse(false)
tasks.named<ProcessResources>("jvmTestProcessResources") {
    if (nativeProbe) {
        val models = file(providers.gradleProperty("modelsDir").get())
        require(models.isAbsolute && models.isDirectory) { "Set -PmodelsDir to an existing absolute model directory." }
        from(models) { into("models") }
    }
}
tasks.withType<Test>().configureEach {
    systemProperty("siren.native", nativeProbe.toString())
    testLogging.showStandardStreams = nativeProbe
}
