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
abstract class PrepareAndroidSpeech : SpeechDownloadTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val modelDirectory: DirectoryProperty
    @get:OutputFile abstract val runtimeAar: RegularFileProperty
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty
    @TaskAction fun prepare() {
        val artifact = SpeechArtifact(SpeechArtifacts.manifest(manifestFile.get().asFile)["runtimes"].asJsonObject["android"].asJsonObject)
        SpeechArtifacts.copyFile(fetch(artifact), runtimeAar.get().asFile)
        val assets = temporaryDir.resolve("assets").apply { deleteRecursively(); mkdirs() }
        modelDirectory.get().asFile.copyRecursively(assets.resolve("models"))
        SpeechArtifacts.publish(assets, assetsDirectory.get().asFile)
    }
}

@CacheableTask
abstract class PrepareIosSpeech : SpeechDownloadTask() {
    @get:OutputDirectory abstract val sherpaFramework: DirectoryProperty
    @get:OutputDirectory abstract val onnxFramework: DirectoryProperty
    @TaskAction fun prepare() {
        val artifacts = SpeechArtifacts.manifest(manifestFile.get().asFile)["runtimes"].asJsonObject["ios"].asJsonArray
        require(artifacts.size() == 2)
        artifacts.forEachIndexed { index, value ->
            val artifact = SpeechArtifact(value.asJsonObject)
            val destination = (if (index == 0) sherpaFramework else onnxFramework).get().asFile
            require(destination.name == File(artifact.destination).name)
            val extracted = temporaryDir.resolve("framework-$index").apply { deleteRecursively() }
            SpeechArtifacts.extract(fetch(artifact), extracted)
            val framework = extracted.walkTopDown().filter { it.isDirectory && it.name.endsWith(".xcframework") }.toList().single()
            SpeechArtifacts.publish(framework, destination)
        }
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
