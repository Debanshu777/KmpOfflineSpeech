import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The license inventory is separate from the SDK's own MIT license and from sample models. */
object NativeRedistributionNotices {
    fun artifacts(manifest: JsonObject): List<SpeechArtifact> =
        manifest["redistribution"].asJsonObject["notices"].asJsonArray.map { SpeechArtifact(it.asJsonObject) }

    fun stage(manifest: JsonObject, output: File, fetch: (SpeechArtifact) -> File) {
        val notices = artifacts(manifest)
        require(notices.map { it.destination }.distinct().size == notices.size) { "Duplicate native notice destination" }
        notices.forEach { notice ->
            require(notice.destination.startsWith("META-INF/siren/licenses/")) { "Native licenses must stay in their metadata namespace" }
            SpeechArtifacts.copyFile(fetch(notice), output.resolve(SpeechArtifacts.relative(notice.destination).toString()))
        }
        manifest["redistribution"].asJsonObject["source_availability"]?.asJsonObject?.let { source ->
            val notice = output.resolve("META-INF/siren/SOURCE-AVAILABILITY.txt")
            notice.parentFile.mkdirs()
            notice.writeText(source["text"].asString, Charsets.UTF_8)
            require(SpeechArtifacts.sha256(notice) == source["sha256"].asString) { "Source availability notice checksum mismatch" }
        }
        val metadata = output.resolve("META-INF/siren/native-redistribution-audit.json")
        metadata.parentFile.mkdirs()
        metadata.writeText(GsonBuilder().setPrettyPrinting().create().toJson(manifest["redistribution"]) + "\n")
        output.resolve("META-INF/siren/README.txt").writeText(
            "Siren first-party source is MIT licensed. Bundled native code has separate licenses.\n" +
                "This directory retains verified upstream license texts and an explicit audit status.\n" +
                "An incomplete audit does not authorize public redistribution. Model weights are not included.\n"
        )
    }

    /** Checks the complete payload set; marking an overall status alone cannot waive missing coverage. */
    fun blockers(manifest: JsonObject): List<String> {
        val audit = manifest["redistribution"]?.asJsonObject ?: return listOf("Missing native redistribution audit")
        val problems = mutableListOf<String>()
        val runtimes = manifest["runtimes"].asJsonObject
        val payloads = buildList {
            add(SpeechArtifact(runtimes["android"].asJsonObject))
            val jvm = runtimes["jvm"].asJsonObject
            add(SpeechArtifact(jvm["wrapper"].asJsonObject))
            jvm["natives"].asJsonArray.forEach { add(SpeechArtifact(it.asJsonObject)) }
            runtimes["ios"].asJsonArray.forEach { add(SpeechArtifact(it.asJsonObject)) }
        }
        val reviews = audit["payloads"].asJsonArray.map { it.asJsonObject }
        if (reviews.map { it["sha256"].asString }.toSet() != payloads.map { it.sha256 }.toSet() || reviews.size != payloads.size) {
            problems += "Redistribution review must cover every pinned native payload exactly once"
        }
        val destinations = artifacts(manifest).map { it.destination }.toSet()
        reviews.forEach { review ->
            if (review["status"].asString != "complete") problems += "Incomplete native payload review: ${review["name"].asString}"
            val references = review["notice_destinations"].asJsonArray.map { it.asString }
            if (references.isEmpty() || references.any { it !in destinations }) problems += "Missing pinned notices for ${review["name"].asString}"
        }
        val components = audit["components"].asJsonArray.map { it.asJsonObject }
        if (!components.map { it["id"].asString }.containsAll(listOf("sherpa-onnx", "onnxruntime-1.28.2", "onnxruntime-1.28.1", "native-transitives"))) {
            problems += "Missing required upstream component reviews"
        }
        components.forEach { component ->
            if (component["status"].asString != "complete") problems += "Incomplete component review: ${component["id"].asString}: ${component["reason"].asString}"
            val sourceRevision = component["source_revision"]?.asString.orEmpty()
            val sourceArchiveHash = component["source_archive_sha256"]?.asString.orEmpty()
            if (!sourceRevision.matches(Regex("[a-f0-9]{40}")) && !sourceArchiveHash.matches(Regex("[a-f0-9]{64}"))) {
                problems += "Missing immutable source identity: ${component["id"].asString}"
            }
            val references = component["notice_destinations"].asJsonArray.map { it.asString }
            if (references.isEmpty() || references.any { it !in destinations }) problems += "Missing pinned component notices: ${component["id"].asString}"
        }
        val fingerprints = audit["binary_build_info"].asJsonArray.map { it.asJsonObject }
        val expectedVariants = setOf("android/arm64-v8a", "android/x86_64", "jvm/linux-aarch64", "jvm/linux-x64", "jvm/osx-aarch64", "jvm/osx-x64", "jvm/win-arm64", "jvm/win-x64", "ios/ios-arm64", "ios/ios-arm64_x86_64-simulator")
        if (fingerprints.map { it["variant"].asString }.toSet() != expectedVariants || fingerprints.size != expectedVariants.size ||
            fingerprints.any { fingerprint -> payloads.none { it.sha256 == fingerprint["archiveSha256"].asString && it.name == fingerprint["archive"].asString } }) {
            problems += "Binary build-info evidence must cover every packaged native variant and match the artifact pins"
        }
        if (audit["status"].asString != "complete") problems += "Overall native redistribution audit is not complete"
        return problems
    }

    /** Add notices to the cinterop KLIB containing the static archives, preserving every other entry. */
    fun embedInKlib(klibFile: File, noticesDirectory: File) {
        require((klibFile.isFile || klibFile.isDirectory) && !Files.isSymbolicLink(klibFile.toPath())) { "Missing cinterop KLIB" }
        val notices = noticesDirectory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(noticesDirectory).invariantSeparatorsPath }.toList()
        require(notices.isNotEmpty()) { "No prepared native notices to embed" }
        val additions = notices.associateBy { file ->
            require(!Files.isSymbolicLink(file.toPath())) { "Refusing notice symlink" }
            val relative = file.relativeTo(noticesDirectory).invariantSeparatorsPath
            require(relative.startsWith("META-INF/siren/")) { "Unexpected KLIB notice namespace" }
            "default/resources/${SpeechArtifacts.relative(relative).toString().replace(File.separatorChar, '/')}"
        }
        // KGP 2.4 produces unpackaged cinterop KLIBs; publication packs them afterward.
        if (klibFile.isDirectory) {
            require(klibFile.resolve("default/manifest").isFile) { "Unexpected unpackaged cinterop KLIB layout" }
            additions.forEach { (name, source) -> SpeechArtifacts.copyFile(source, klibFile.resolve(name)) }
            return
        }
        val temporary = Files.createTempFile(klibFile.parentFile.toPath(), ".siren-notices-", ".klib").toFile()
        try {
            ZipFile(klibFile).use { source ->
                require(source.getEntry("default/manifest") != null) { "Unexpected cinterop KLIB layout" }
                ZipOutputStream(temporary.outputStream().buffered()).use { output ->
                    val seen = mutableSetOf<String>()
                    source.entries().asSequence().forEach { entry ->
                        SpeechArtifacts.relative(entry.name)
                        require(seen.add(entry.name)) { "Duplicate KLIB entry: ${entry.name}" }
                        if (entry.name !in additions) {
                            output.putNextEntry(ZipEntry(entry.name).apply { time = 315532800000L })
                            if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                    additions.forEach { (name, file) ->
                        output.putNextEntry(ZipEntry(name).apply { time = 315532800000L })
                        file.inputStream().use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
            }
            SpeechArtifacts.copyFile(temporary, klibFile)
        } finally { temporary.delete() }
    }
}

/** Public binary publication must run this gate. Local preparation remains usable while audit is open. */
@DisableCachingByDefault(because = "Release gate must recheck pinned notice availability and audit coverage")
abstract class VerifyNativeRedistribution : SpeechDownloadTask() {
    @get:OutputFile abstract val reportFile: RegularFileProperty
    init { outputs.upToDateWhen { false } }
    @TaskAction fun verifyNotices() {
        val manifest = SpeechArtifacts.manifest(manifestFile.get().asFile)
        val problems = NativeRedistributionNotices.blockers(manifest).toMutableList()
        NativeRedistributionNotices.artifacts(manifest).forEach { notice ->
            try { fetch(notice) } catch (error: Exception) { problems += "Cannot verify ${notice.name}: ${error.message}" }
        }
        val report = JsonObject().apply {
            addProperty("ready", problems.isEmpty())
            add("blockers", GsonBuilder().create().toJsonTree(problems))
            add("audit", manifest["redistribution"])
        }
        reportFile.get().asFile.apply { parentFile.mkdirs(); writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n") }
        if (problems.isNotEmpty()) throw GradleException("Native redistribution is not ready for public binary release:\n" + problems.joinToString("\n") { " - $it" })
    }
}
