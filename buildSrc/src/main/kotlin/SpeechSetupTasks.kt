import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File

abstract class SpeechDownloadTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val manifestFile: RegularFileProperty
    @get:LocalState abstract val downloadCache: DirectoryProperty
    @get:Input abstract val offline: Property<Boolean>
    protected fun fetch(artifact: SpeechArtifact) = SpeechArtifacts.fetch(artifact, downloadCache.get().asFile, offline.get())
}

@CacheableTask
abstract class PrepareSpeechModels : SpeechDownloadTask() {
    @get:OutputDirectory abstract val modelDirectory: DirectoryProperty
    @TaskAction fun prepare() {
        val models = SpeechArtifacts.manifest(manifestFile.get().asFile)["models"].asJsonObject
        val stage = temporaryDir.resolve("models").apply { deleteRecursively(); mkdirs() }
        models["files"].asJsonArray.forEach { value ->
            val artifact = SpeechArtifact(value.asJsonObject)
            require(artifact.destination.startsWith("models/"))
            SpeechArtifacts.copyFile(fetch(artifact), stage.resolve(SpeechArtifacts.relative(artifact.destination.removePrefix("models/")).toString()))
        }
        val archive = SpeechArtifact(models["kitten_archive"].asJsonObject)
        val extracted = temporaryDir.resolve("kitten").apply { deleteRecursively() }
        SpeechArtifacts.extract(fetch(archive), extracted)
        archive.json["files"].asJsonArray.forEach { value ->
            val artifact = SpeechArtifact(value.asJsonObject)
            val file = extracted.walkTopDown().filter { it.isFile && it.name == artifact.name }.toList().single()
            SpeechArtifacts.verify(file, artifact)
            SpeechArtifacts.copyFile(file, stage.resolve("kitten/${artifact.name}"))
        }
        extracted.walkTopDown().filter { it.isFile && it.name.lowercase().let { name -> listOf("license", "copying", "notice").any(name::startsWith) } }
            .forEach { SpeechArtifacts.copyFile(it, stage.resolve("kitten/${it.name}")) }
        SpeechArtifacts.publish(stage, modelDirectory.get().asFile)
    }
}

@CacheableTask
abstract class PrepareAndroidRuntime : SpeechDownloadTask() {
    @get:OutputFile abstract val runtimeAar: RegularFileProperty
    @get:OutputFile abstract val classesJar: RegularFileProperty
    @get:OutputDirectory abstract val jniLibsDirectory: DirectoryProperty
    @get:OutputDirectory abstract val noticesDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val manifest = SpeechArtifacts.manifest(manifestFile.get().asFile)
        val artifact = SpeechArtifact(manifest["runtimes"].asJsonObject["android"].asJsonObject)
        val archive = fetch(artifact)
        require(runtimeAar.get().asFile.name == artifact.name) { "Runtime archive output must preserve the pinned artifact name" }
        // AARs are ZIP files; the safe extractor dispatches on a .zip suffix.
        val zip = temporaryDir.resolve("runtime.zip")
        SpeechArtifacts.copyFile(archive, zip)
        val extracted = temporaryDir.resolve("runtime-extracted").apply { deleteRecursively() }
        SpeechArtifacts.extract(zip, extracted)
        val wrapper = extracted.resolve("classes.jar")
        check(wrapper.isFile && wrapper.length() > 0) { "The pinned runtime contains no classes.jar" }
        val upstreamRules = extracted.resolve("proguard.txt")
        check(!upstreamRules.exists() || upstreamRules.readText().isBlank()) {
            "Upstream consumer rules changed; reconcile the SDK consumer rules before updating the runtime"
        }
        val jni = temporaryDir.resolve("jniLibs").apply { deleteRecursively(); mkdirs() }
        val supportedAbis = listOf("arm64-v8a", "x86_64")
        val expectedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-c-api.so", "libsherpa-onnx-cxx-api.so", "libsherpa-onnx-jni.so")
        for (abi in supportedAbis) {
            val source = extracted.resolve("jni/$abi")
            val libraries = source.listFiles()?.filter { it.isFile && it.extension == "so" }.orEmpty()
            check(libraries.map { it.name }.toSet() == expectedLibraries) { "Unexpected native runtime libraries for $abi" }
            libraries.forEach { library ->
                check(library.length() > 0) { "Empty native runtime library: ${library.name}" }
                SpeechArtifacts.copyFile(library, jni.resolve("$abi/${library.name}"))
            }
        }
        val notices = temporaryDir.resolve("runtime-notices").apply { deleteRecursively(); mkdirs() }
        NativeRedistributionNotices.stage(manifest, notices, ::fetch)
        extracted.walkTopDown().filter { file ->
            file.isFile && listOf("license", "notice", "copying", "copyright").any(file.name.lowercase()::startsWith)
        }.forEach { file ->
            SpeechArtifacts.copyFile(file, notices.resolve("META-INF/siren/upstream/${file.relativeTo(extracted).invariantSeparatorsPath}"))
        }
        artifact.json["notices"]?.asJsonArray?.forEach { value ->
            val notice = SpeechArtifact(value.asJsonObject)
            require(notice.destination.startsWith("META-INF/siren/")) { "Runtime notices must stay in their metadata namespace" }
            SpeechArtifacts.copyFile(fetch(notice), notices.resolve(SpeechArtifacts.relative(notice.destination).toString()))
        }
        notices.resolve("META-INF/siren/runtime-provenance.txt").apply {
            parentFile.mkdirs()
            writeText("""
                Upstream runtime: ${artifact.name}
                Source: ${artifact.json["url"].asString}
                SHA-256: ${artifact.sha256}
                Packaged ABIs: ${supportedAbis.joinToString(", ")}
                The upstream classes.jar and selected native library bytes are copied without modification.
                No model weights, model assets, application UI, or Android resources are included.
                Retain the accompanying upstream license and notice files when redistributing this runtime.
            """.trimIndent() + "\n")
        }
        SpeechArtifacts.copyFile(archive, runtimeAar.get().asFile)
        SpeechArtifacts.copyFile(wrapper, classesJar.get().asFile)
        SpeechArtifacts.publish(jni, jniLibsDirectory.get().asFile)
        SpeechArtifacts.publish(notices, noticesDirectory.get().asFile)
    }
}

/** The sample's weights are deliberately separate from the SDK runtime preparation. */
@CacheableTask
abstract class PrepareAndroidSpeech : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val modelDirectory: DirectoryProperty
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty
    @TaskAction fun prepare() {
        val assets = temporaryDir.resolve("assets").apply { deleteRecursively(); mkdirs() }
        modelDirectory.get().asFile.copyRecursively(assets.resolve("models"))
        SpeechArtifacts.publish(assets, assetsDirectory.get().asFile)
    }
}

@CacheableTask
abstract class PrepareIosSpeech : SpeechDownloadTask() {
    @get:OutputDirectory abstract val sherpaFramework: DirectoryProperty
    @get:OutputDirectory abstract val onnxFramework: DirectoryProperty
    @get:OutputDirectory abstract val noticesDirectory: DirectoryProperty
    @TaskAction fun prepare() {
        val manifest = SpeechArtifacts.manifest(manifestFile.get().asFile)
        val artifacts = manifest["runtimes"].asJsonObject["ios"].asJsonArray
        require(artifacts.size() == 2)
        val notices = temporaryDir.resolve("ios-notices").apply { deleteRecursively(); mkdirs() }
        NativeRedistributionNotices.stage(manifest, notices, ::fetch)
        artifacts.forEachIndexed { index, value ->
            val artifact = SpeechArtifact(value.asJsonObject)
            val destination = (if (index == 0) sherpaFramework else onnxFramework).get().asFile
            require(destination.name == File(artifact.destination).name)
            val extracted = temporaryDir.resolve("framework-$index").apply { deleteRecursively() }
            SpeechArtifacts.extract(fetch(artifact), extracted)
            // License files may be siblings of an XCFramework, so inspect the whole release archive.
            extracted.walkTopDown().filter { file ->
                file.isFile && listOf("license", "notice", "copying", "copyright").any(file.name.lowercase()::startsWith)
            }.forEach { file ->
                SpeechArtifacts.copyFile(file, notices.resolve("META-INF/siren/ios-upstream/$index/${file.relativeTo(extracted).invariantSeparatorsPath}"))
            }
            val framework = extracted.walkTopDown().filter { it.isDirectory && it.name.endsWith(".xcframework") }.toList().single()
            SpeechArtifacts.publish(framework, destination)
        }
        SpeechArtifacts.publish(notices, noticesDirectory.get().asFile)
    }
}

@DisableCachingByDefault(because = "Compiles with the locally installed Apple SDK and clang")
abstract class PrepareIosAudio : DefaultTask() {
    @get:Input abstract val target: Property<String>
    @get:Input abstract val deploymentTarget: Property<String>
    @get:Input abstract val toolchainIdentity: Property<String>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val bridgeDirectory: DirectoryProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sherpaHeaders: DirectoryProperty
    @get:OutputFile abstract val archiveFile: RegularFileProperty
    @TaskAction fun compile() {
        val simulator = target.get() == "iosSimulatorArm64"
        require(simulator || target.get() == "iosArm64")
        val sdk = if (simulator) "iphonesimulator" else "iphoneos"
        val sdkPath = command(listOf("xcrun", "--sdk", sdk, "--show-sdk-path")).trim()
        val triple = "arm64-apple-ios${deploymentTarget.get()}" + if (simulator) "-simulator" else ""
        val source = bridgeDirectory.get().asFile
        val output = archiveFile.get().asFile.apply { parentFile.mkdirs() }
        val objectFile = temporaryDir.resolve("speech_audio.o")
        command(listOf("xcrun", "--sdk", sdk, "clang++", "-std=c++17", "-fobjc-arc", "-O2", "-target", triple,
            "-isysroot", sdkPath, "-I", source.absolutePath, "-I", sherpaHeaders.get().asFile.absolutePath,
            "-c", source.resolve("speech_audio.mm").absolutePath, "-o", objectFile.absolutePath))
        val staged = temporaryDir.resolve("libspeech_audio.a")
        command(listOf("xcrun", "libtool", "-static", "-o", staged.absolutePath, objectFile.absolutePath))
        SpeechArtifacts.copyFile(staged, output)
    }
    private fun command(arguments: List<String>): String {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "Command failed: ${arguments.joinToString(" ")}\n$output" }
        return output
    }
}
