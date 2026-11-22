import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Packages the official Java API and resource-based desktop native loader without model weights. */
@CacheableTask
abstract class PrepareJvmRuntime : SpeechDownloadTask() {
    @get:OutputFile abstract val wrapperJar: RegularFileProperty
    @get:OutputDirectory abstract val embeddedClassesDirectory: DirectoryProperty
    @get:OutputDirectory abstract val resourcesDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val manifest = SpeechArtifacts.manifest(manifestFile.get().asFile)
        val runtime = manifest["runtimes"].asJsonObject["jvm"].asJsonObject
        val wrapper = SpeechArtifact(runtime["wrapper"].asJsonObject)
        val wrapperArchive = fetch(wrapper)
        val wrapperFiles = extractJar(wrapperArchive, "wrapper")
        val classes = temporaryDir.resolve("classes").apply { deleteRecursively(); mkdirs() }
        val packageDirectory = wrapperFiles.resolve("com/k2fsa/sherpa/onnx")
        check(packageDirectory.isDirectory) { "The pinned JVM wrapper contains no sherpa Java classes." }
        packageDirectory.walkTopDown().filter { it.isFile }.forEach { file ->
            check(file.extension == "class") { "Unexpected Java API package payload: ${file.name}" }
            SpeechArtifacts.copyFile(file, classes.resolve("com/k2fsa/sherpa/onnx/${file.relativeTo(packageDirectory).invariantSeparatorsPath}"))
        }
        val resources = temporaryDir.resolve("resources").apply { deleteRecursively(); mkdirs() }
        NativeRedistributionNotices.stage(manifest, resources, ::fetch)
        retainNotices(wrapperFiles, resources, "wrapper")
        val expectedPlatforms = setOf("linux-aarch64", "linux-x64", "osx-aarch64", "osx-x64", "win-arm64", "win-x64")
        val platforms = mutableSetOf<String>()
        runtime["natives"].asJsonArray.forEach { value ->
            val artifact = SpeechArtifact(value.asJsonObject)
            val platform = artifact.json["platform"].asString
            check(platform in expectedPlatforms && platforms.add(platform)) { "Unsupported or duplicate desktop native platform: $platform" }
            val destination = "sherpa-onnx/native/$platform"
            require(artifact.destination == destination) { "Native classpath destination does not match the upstream loader." }
            val extracted = extractJar(fetch(artifact), platform)
            val source = extracted.resolve(destination)
            val expectedFiles = when {
                platform.startsWith("osx-") -> setOf("libonnxruntime.dylib", "libsherpa-onnx-jni.dylib")
                platform.startsWith("win-") -> setOf("onnxruntime.dll", "sherpa-onnx-jni.dll")
                else -> setOf("libonnxruntime.so", "libsherpa-onnx-jni.so")
            }
            val files = source.listFiles()?.filter { it.isFile }.orEmpty()
            check(files.map { it.name }.toSet() == expectedFiles) { "Unexpected native files for $platform" }
            files.forEach { file ->
                check(file.length() > 0) { "Empty desktop native library: ${file.name}" }
                SpeechArtifacts.copyFile(file, resources.resolve("$destination/${file.name}"))
            }
            retainNotices(extracted, resources, platform)
        }
        check(platforms == expectedPlatforms) { "The portable JVM runtime must include all six pinned platforms." }
        runtime["notices"]?.asJsonArray?.forEach { value ->
            val notice = SpeechArtifact(value.asJsonObject)
            require(notice.destination.startsWith("META-INF/siren/"))
            SpeechArtifacts.copyFile(fetch(notice), resources.resolve(SpeechArtifacts.relative(notice.destination).toString()))
        }
        resources.resolve("META-INF/siren/jvm-runtime-provenance.txt").apply {
            parentFile.mkdirs()
            writeText("""
                sherpa-onnx JVM API: ${wrapper.name}
                Source: ${wrapper.json["url"].asString}
                SHA-256: ${wrapper.sha256}
                Native classpath platforms: ${platforms.sorted().joinToString(", ")}
                The Java wrapper classes and native library bytes are unmodified upstream payloads.
                The upstream LibraryLoader selects OS/architecture and loads native classpath resources.
                No model weights, sample assets, or Compose UI are packaged in this SDK runtime.
                Platform payload availability does not imply that every operating system has been execution-tested.
            """.trimIndent() + "\n")
        }
        SpeechArtifacts.copyFile(wrapperArchive, wrapperJar.get().asFile)
        SpeechArtifacts.publish(classes, embeddedClassesDirectory.get().asFile)
        SpeechArtifacts.publish(resources, resourcesDirectory.get().asFile)
    }

    private fun extractJar(archive: File, name: String): File {
        val zip = temporaryDir.resolve("$name.zip")
        SpeechArtifacts.copyFile(archive, zip)
        val output = temporaryDir.resolve("extracted-$name").apply { deleteRecursively() }
        SpeechArtifacts.extract(zip, output)
        return output
    }

    private fun retainNotices(source: File, resources: File, label: String) {
        source.walkTopDown().filter { file ->
            file.isFile && listOf("license", "notice", "copying", "copyright").any(file.name.lowercase()::startsWith)
        }.forEach { file ->
            SpeechArtifacts.copyFile(file, resources.resolve("META-INF/siren/jvm-upstream/$label/${file.relativeTo(source).invariantSeparatorsPath}"))
        }
    }
}
