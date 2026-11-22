import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

object ModelAddonRelease {
    const val central = "https://repo.maven.apache.org/maven2/"
    private val stable = Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)")
    fun version(configured: String, confirmed: String, ref: String): String {
        require(stable.matches(configured) && configured == confirmed) { "Confirm the exact stable sirenModelsVersion" }
        require(ref == "refs/heads/main") { "Model add-on releases must use main" }
        return "models-v$configured"
    }
    fun prefixes(version: String): Pair<String, String> {
        require(stable.matches(version))
        return "io/github/debanshu777/siren-models-gradle-plugin/$version/siren-models-gradle-plugin-$version" to
            "io/github/debanshu777/siren/models/io.github.debanshu777.siren.models.gradle.plugin/$version/io.github.debanshu777.siren.models.gradle.plugin-$version"
    }
    fun paths(version: String): List<String> {
        val (implementation, marker) = prefixes(version)
        val files = listOf("$implementation.pom", "$implementation.module", "$implementation.jar",
            "$implementation-sources.jar", "$implementation-javadoc.jar", "$marker.pom")
        return files.flatMap { listOf(it, "$it.asc") }
    }
    fun notes(changelog: String, version: String): String {
        val heading = "## [Models $version]"
        val lines = changelog.lines()
        require(lines.count { it.trim() == heading } == 1) { "Add exactly one $heading changelog section" }
        val start = lines.indexOfFirst { it.trim() == heading } + 1
        val end = (start until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(start, end).joinToString("\n").trim().also {
            require(it.isNotBlank() && !Regex("\\b(TODO|TBD)\\b").containsMatchIn(it)) { "Finalize model add-on release notes" }
        }
    }
    fun status(url: String, token: String = ""): Int {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.connectTimeout = 15_000; c.readTimeout = 30_000; c.useCaches = false
        c.setRequestProperty("User-Agent", "Siren-model-release")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        return try { c.responseCode } finally { c.disconnect() }
    }
    fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(1024 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun json(file: File, data: Any) { file.parentFile.mkdirs(); file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n") }
}

@DisableCachingByDefault(because = "Checks mutable remote release state")
abstract class ValidateModelAddonRelease : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:Input abstract val confirmedVersion: Property<String>
    @get:Input abstract val branchRef: Property<String>
    @get:Input abstract val expectedCommit: Property<String>
    @get:Internal abstract val githubToken: Property<String>
    @get:Internal abstract val githubOutput: Property<String>
    @get:Internal abstract val repositoryRoot: DirectoryProperty
    @get:InputFile abstract val changelog: RegularFileProperty
    @get:OutputFile abstract val report: RegularFileProperty
    @get:OutputFile abstract val releaseNotes: RegularFileProperty
    init { outputs.upToDateWhen { false } }
    @TaskAction fun validate() {
        val version = releaseVersion.get()
        val tag = ModelAddonRelease.version(version, confirmedVersion.get(), branchRef.get())
        fun git(vararg args: String): String {
            val p = ProcessBuilder(listOf("git") + args).directory(repositoryRoot.get().asFile).redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText().trim()
            check(p.waitFor() == 0) { "Git release validation failed" }; return output
        }
        check(git("status", "--porcelain").isBlank()) { "Release checkout must be clean" }
        val commit = git("rev-parse", "HEAD")
        check(commit == expectedCommit.get()) { "Checkout differs from dispatched commit" }
        check(ModelAddonRelease.status("https://api.github.com/repos/Debanshu777/Siren/git/ref/tags/$tag", githubToken.get()) == 404) {
            "Model release tag already exists or absence could not be verified"
        }
        val (implementation, marker) = ModelAddonRelease.prefixes(version)
        listOf(implementation, marker).forEach { prefix ->
            check(ModelAddonRelease.status(ModelAddonRelease.central + prefix + ".pom") == 404) { "Central coordinate exists or absence could not be verified" }
        }
        val notes = ModelAddonRelease.notes(changelog.get().asFile.readText(), version)
        releaseNotes.get().asFile.apply { parentFile.mkdirs() }.writeText("Siren model add-on $version\n\nBuilt from `$commit`.\n\n$notes\n")
        ModelAddonRelease.json(report.get().asFile, mapOf("version" to version, "tag" to tag, "commit" to commit))
        if (githubOutput.get().isNotBlank()) File(githubOutput.get()).appendText("version=$version\ntag=$tag\n")
        logger.lifecycle("Validated unpublished model add-on $version at $commit")
    }
}

@DisableCachingByDefault(because = "Inspects the locally staged publication")
abstract class VerifyModelAddonArtifacts : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:InputDirectory abstract val stagingRepository: DirectoryProperty
    @get:OutputFile abstract val report: RegularFileProperty
    @TaskAction fun verify() {
        val (implementation, marker) = ModelAddonRelease.prefixes(releaseVersion.get())
        val root = stagingRepository.get().asFile
        val paths = listOf("$implementation.pom", "$implementation.module", "$implementation.jar",
            "$implementation-sources.jar", "$implementation-javadoc.jar", "$marker.pom")
        paths.forEach { check(root.resolve(it).isFile && root.resolve(it).length() > 0) { "Missing staged artifact: $it" } }
        paths.filter { it.endsWith(".jar") }.forEach { path ->
            val file = root.resolve(path)
            check(file.length() < 5L * 1024 * 1024) { "Unexpected model plugin payload size" }
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                check(entries.none { it.endsWith(".onnx") || it.endsWith(".bin") }) { "Plugin publication contains model weights" }
                if (path.endsWith("-javadoc.jar")) check(entries.any { it.endsWith(".html") }) { "Dokka API documentation is missing" }
                if (path == "$implementation.jar") check("siren-models/catalog.json" in entries &&
                    "io/github/debanshu/siren/gradle/SirenModelsPlugin.class" in entries) { "Plugin implementation/catalog missing" }
            }
        }
        val markerText = root.resolve("$marker.pom").readText()
        check(markerText.contains("<artifactId>siren-models-gradle-plugin</artifactId>")) { "Marker does not select the implementation" }
        ModelAddonRelease.json(report.get().asFile, mapOf("verified" to true, "version" to releaseVersion.get(),
            "files" to paths.associateWith { mapOf("bytes" to root.resolve(it).length(), "sha256" to ModelAddonRelease.hash(root.resolve(it))) }))
    }
}

@DisableCachingByDefault(because = "Verifies public Central delivery")
abstract class VerifyModelAddonPublication : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:InputFile abstract val candidateJar: RegularFileProperty
    @get:OutputFile abstract val report: RegularFileProperty
    init { outputs.upToDateWhen { false } }
    @TaskAction fun verify() {
        repeat(90) { attempt ->
            val statuses = ModelAddonRelease.paths(releaseVersion.get()).associateWith { ModelAddonRelease.status(ModelAddonRelease.central + it) }
            ModelAddonRelease.json(report.get().asFile, mapOf("verified" to false, "files" to statuses, "attempt" to attempt + 1))
            check(statuses.values.all { it == 200 || it == 404 }) { "Unexpected Central response; inspect delivery report" }
            if (statuses.values.all { it == 200 }) {
                val prefix = ModelAddonRelease.prefixes(releaseVersion.get()).first
                val downloaded = temporaryDir.resolve("public.jar")
                val c = URI(ModelAddonRelease.central + prefix + ".jar").toURL().openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }
                c.getInputStream().use { input -> downloaded.outputStream().use { output ->
                    val bytes = input.readNBytes(5 * 1024 * 1024 + 1)
                    check(bytes.size <= 5 * 1024 * 1024); output.write(bytes)
                } }
                val candidateHash = ModelAddonRelease.hash(candidateJar.get().asFile)
                check(ModelAddonRelease.hash(downloaded) == candidateHash) { "Public plugin JAR differs from signed candidate" }
                ModelAddonRelease.json(report.get().asFile, mapOf("verified" to true, "files" to statuses,
                    "attempt" to attempt + 1, "candidateJarSha256" to candidateHash))
                logger.lifecycle("Verified 12 public model plugin files and exact candidate JAR hash"); return
            }
            check(attempt < 89) { "Central propagation incomplete" }
            logger.lifecycle("Waiting for model add-on delivery (${attempt + 1}/90)")
            Thread.sleep(20_000)
        }
    }
}
