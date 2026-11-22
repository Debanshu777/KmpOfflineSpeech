import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.github.debanshu.siren.gradle.PrepareSirenModels
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    kotlin("multiplatform") version "2.4.21"
    id("io.github.debanshu777.siren.models")
    id("com.android.kotlin.multiplatform.library") version "9.5.1"
    id("com.android.application") version "9.5.1" apply false
}

kotlin {
    android {
        namespace = "io.github.debanshu.siren.validation.consumer"
        compileSdk = 37
        minSdk = 27
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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

val nativeProbe = providers.gradleProperty("native").map(String::toBoolean).getOrElse(false)
tasks.withType<Test>().configureEach {
    systemProperty("siren.native", nativeProbe.toString())
    testLogging.showStandardStreams = nativeProbe
}

sirenModels {
    providers.gradleProperty("sirenVersion").orNull?.let { sdkVersion.set(it) }
    bundleModels.set(providers.gradleProperty("bundleModels").map(String::toBoolean).orElse(true))
    providers.gradleProperty("modelCache").orNull?.let { cacheDirectory.set(file(it)) }
    recognition.set(providers.gradleProperty("recognition").map(String::toBoolean).orElse(true))
    synthesis.set(providers.gradleProperty("synthesis").map(String::toBoolean).orElse(true))
}

abstract class VerifyModelPackaging : DefaultTask() {
    @get:Input abstract val catalog: Property<String>
    @get:Input abstract val bundleModels: Property<Boolean>
    @get:Input abstract val sdkVersion: Property<String>
    @get:Input abstract val recognition: Property<Boolean>
    @get:Input abstract val synthesis: Property<Boolean>
    @get:InputFile abstract val androidApk: RegularFileProperty
    @get:InputFile abstract val jvmJar: RegularFileProperty
    @get:Optional @get:InputDirectory abstract val iosModels: DirectoryProperty
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction fun verify() {
        val data = (JsonSlurper().parseText(catalog.get()) as Map<*, *>)["models"] as Map<*, *>
        val expected = linkedMapOf<String, Map<*, *>>()
        (data["files"] as List<*>).forEach { raw ->
            val entry = raw as Map<*, *>
            val path = entry["destination"].toString().removePrefix("models/")
            if (bundleModels.get() && (path.startsWith("whisper/") && recognition.get() || path.startsWith("kitten/") && synthesis.get())) expected[path] = entry
        }
        if (bundleModels.get() && synthesis.get()) ((data["kitten_archive"] as Map<*, *>)["files"] as List<*>).forEach { raw ->
            val entry = raw as Map<*, *>
            expected["kitten/${entry["name"]}"] = entry
        }
        fun hash(input: java.io.InputStream): String = input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun archive(file: java.io.File, prefix: String): Long = ZipFile(file).use { zip ->
            expected.forEach { (path, metadata) ->
                val entry = checkNotNull(zip.getEntry(prefix + path)) { "Missing model in ${file.name}: $path" }
                check(entry.size == (metadata["bytes"] as Number).toLong() && hash(zip.getInputStream(entry)) == metadata["sha256"]) {
                    "Packaged model differs from pinned bytes: $path"
                }
            }
            if (!bundleModels.get()) {
                check(zip.entries().asSequence().none { it.name.startsWith(prefix) }) { "Opted-out archive contains generated models" }
                return@use 0L
            }
            check(zip.getEntry(prefix + "SIREN-MODELS-NOTICE.txt") != null)
            if (recognition.get()) check(zip.getEntry(prefix + "whisper/LICENSE") != null)
            else check(zip.entries().asSequence().none { it.name.startsWith(prefix + "whisper/") })
            if (synthesis.get()) check(zip.getEntry(prefix + "kitten/LICENSE") != null)
            else check(zip.entries().asSequence().none { it.name.startsWith(prefix + "kitten/") })
            expected.keys.sumOf { zip.getEntry(prefix + it).compressedSize }
        }
        val androidCompressed = archive(androidApk.get().asFile, "assets/models/")
        val jvmCompressed = archive(jvmJar.get().asFile, "models/")
        val ios = iosModels.orNull?.asFile
        expected.forEach { (path, metadata) ->
            val file = checkNotNull(ios).resolve(path)
            check(file.isFile && file.length() == (metadata["bytes"] as Number).toLong() && hash(file.inputStream()) == metadata["sha256"]) {
                "iOS resource differs from pinned bytes: $path"
            }
        }
        if (bundleModels.get()) {
            if (!recognition.get()) check(!checkNotNull(ios).resolve("whisper").exists())
            if (!synthesis.get()) check(!checkNotNull(ios).resolve("kitten").exists())
        } else check(ios == null)
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf(
            "bundleModels" to bundleModels.get(), "passed" to true, "modelFiles" to expected.size, "recognition" to recognition.get(), "synthesis" to synthesis.get(),
            "rawModelBytes" to expected.values.sumOf { (it["bytes"] as Number).toLong() },
            "androidApkCompressedModelBytes" to androidCompressed, "jvmJarCompressedModelBytes" to jvmCompressed,
            "androidApkBytes" to androidApk.get().asFile.length(), "jvmJarBytes" to jvmJar.get().asFile.length(),
            "iosResourcesVerified" to bundleModels.get(), "sdkVersion" to sdkVersion.get()
        ))) + "\n")
        if (bundleModels.get()) logger.lifecycle("Verified ${expected.size} pinned models in Android APK, JVM JAR and iOS resource output")
        else logger.lifecycle("Verified model opt-out: no generated Android/JVM model files; iOS copying disabled")
    }
}

val prepareModels = tasks.named<PrepareSirenModels>("prepareSirenModels")
tasks.register<VerifyModelPackaging>("verifyModelPackaging") {
    dependsOn("jvmJar", ":androidApp:assembleDebug", "copySirenModelsForXcode")
    catalog.set(prepareModels.map { it.catalogJson })
    bundleModels.set(sirenModels.bundleModels)
    sdkVersion.set(sirenModels.sdkVersion)
    recognition.set(sirenModels.recognition)
    synthesis.set(sirenModels.synthesis)
    androidApk.set(project(":androidApp").layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk"))
    jvmJar.set(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile })
    val bundled = sirenModels.bundleModels
    iosModels.set(layout.dir(tasks.named<Sync>("copySirenModelsForXcode").map { it.destinationDir }).filter { bundled.get() })
    report.set(layout.buildDirectory.file("reports/model-packaging.json"))
}
