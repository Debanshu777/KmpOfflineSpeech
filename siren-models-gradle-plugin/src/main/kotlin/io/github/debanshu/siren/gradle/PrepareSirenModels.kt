package io.github.debanshu.siren.gradle

import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class PrepareSirenModels : DefaultTask() {
    @get:Input abstract val recognition: Property<Boolean>
    @get:Input abstract val synthesis: Property<Boolean>
    @get:Input abstract val offline: Property<Boolean>
    @get:LocalState abstract val downloadCache: DirectoryProperty
    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty
    @get:Input val catalogJson: String = checkNotNull(PrepareSirenModels::class.java.getResourceAsStream("/siren-models/catalog.json")) {
        "Missing model catalog"
    }.bufferedReader().use { it.readText() }
    @get:Input val whisperLicenseText: String = checkNotNull(
        PrepareSirenModels::class.java.getResourceAsStream("/siren-models/WHISPER-LICENSE.txt")
    ).bufferedReader().use { it.readText() }

    @TaskAction fun prepare() {
        val catalog = JsonParser.parseString(catalogJson).asJsonObject
        val models = catalog["models"].asJsonObject
        val stage = temporaryDir.resolve("assets").apply { deleteRecursively(); mkdirs() }
        val root = stage.resolve("models").apply { mkdirs() }
        fun fetch(artifact: ModelArtifact): File = ModelArtifacts.fetch(artifact, downloadCache.get().asFile, offline.get())
        models["files"].asJsonArray.forEach { value ->
            val artifact = ModelArtifact(value.asJsonObject)
            val selected = when {
                artifact.destination.startsWith("models/whisper/") -> recognition.get()
                artifact.destination.startsWith("models/kitten/") -> synthesis.get()
                else -> error("Unsupported model catalog path: ${artifact.destination}")
            }
            if (selected) ModelArtifacts.copyFile(fetch(artifact), stage.resolve(ModelArtifacts.relative(artifact.destination).toString()))
        }
        if (synthesis.get()) {
            val archive = ModelArtifact(models["kitten_archive"].asJsonObject)
            val extracted = temporaryDir.resolve("kitten").apply { deleteRecursively() }
            ModelArtifacts.extract(fetch(archive), extracted)
            archive.json["files"].asJsonArray.forEach { value ->
                val artifact = ModelArtifact(value.asJsonObject)
                val file = extracted.walkTopDown().filter { it.isFile && it.name == artifact.name }.toList().single()
                ModelArtifacts.verify(file, artifact)
                ModelArtifacts.copyFile(file, root.resolve("kitten/${artifact.name}"))
            }
            extracted.walkTopDown().filter { file ->
                file.isFile && listOf("license", "copying", "notice", "copyright").any(file.name.lowercase()::startsWith)
            }.forEach { file -> ModelArtifacts.copyFile(file, root.resolve("kitten/${file.name}")) }
        }
        if (recognition.get()) {
            root.resolve("whisper/LICENSE").apply {
                parentFile.mkdirs()
                writeText(whisperLicenseText)
            }
        }
        root.resolve("SIREN-MODELS-NOTICE.txt").writeText("""
            Optional Siren model files, selected at application build time.
            Recognition: ${recognition.get()}; synthesis: ${synthesis.get()}.
            Whisper: https://github.com/openai/whisper (MIT; accompanying LICENSE).
            Pinned ONNX export: https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c
            Kitten: https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8 (retain the archive's accompanying LICENSE).
            Converted Kitten files and lexicon: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
            Files are verified against the plugin's pinned catalog. Model/data terms are independent of Siren's source license.
            The app owns these files. No runtime download or network permission is added.
        """.trimIndent() + "\n")
        ModelArtifacts.publish(stage, assetsDirectory.get().asFile)
    }
}
