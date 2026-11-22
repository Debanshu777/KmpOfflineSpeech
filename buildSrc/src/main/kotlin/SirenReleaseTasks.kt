import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class SirenReleaseVersion(val version: String, val tag: String, val previousTag: String)

/** Pure guards are shared by the release task and offline regression tests. */
object SirenReleaseChecks {
    private val stable = Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)")
    private const val central = "https://repo.maven.apache.org/maven2"
    private const val groupPath = "io/github/debanshu777"

    fun configuredVersion(properties: String): String {
        val entries = properties.lineSequence().map(String::trim)
            .mapNotNull { Regex("sirenVersion\\s*=\\s*(\\S+)").matchEntire(it)?.groupValues?.get(1) }.toList()
        require(entries.size == 1) { "gradle.properties must contain exactly one sirenVersion" }
        return entries.single()
    }

    fun requireCleanWorkspace(status: String) {
        require(status.isBlank()) { "Release checkout must be clean; commit the reviewed source before releasing" }
    }

    private fun compare(left: String, right: String): Int {
        left.split('.').zip(right.split('.')).forEach { (a, b) ->
            val comparison = a.toBigInteger().compareTo(b.toBigInteger())
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun validateVersion(configured: String, confirmed: String, ref: String, tags: List<String>): SirenReleaseVersion {
        require(ref == "refs/heads/main") { "Release dispatch must use the main branch" }
        require(stable.matches(configured)) { "sirenVersion must be a stable three-component version" }
        require(confirmed == configured) { "confirmVersion must exactly match configured sirenVersion" }
        val previous = tags.filter { it.startsWith('v') && stable.matches(it.drop(1)) }
            .maxWithOrNull { a, b -> compare(a.drop(1), b.drop(1)) }.orEmpty()
        require(previous.isEmpty() || compare(configured, previous.drop(1)) > 0) {
            "Version must be newer than the existing stable tag $previous"
        }
        return SirenReleaseVersion(configured, "v$configured", previous)
    }

    fun changelogSection(markdown: String, version: String): String {
        val lines = markdown.lines()
        val heading = Regex("^##\\s+\\[?${Regex.escape(version)}\\]?(?:\\s|$)")
        val starts = lines.indices.filter { heading.containsMatchIn(lines[it]) }
        require(starts.size == 1) { "CHANGELOG.md needs exactly one curated $version section" }
        val start = starts.single()
        val end = ((start + 1)..lines.lastIndex).firstOrNull { Regex("^##\\s").containsMatchIn(lines[it]) } ?: lines.size
        val section = lines.subList(start + 1, end).joinToString("\n").trim()
        require(section.isNotBlank() && !Regex("\\b(TODO|TBD)\\b").containsMatchIn(section)) {
            "Release notes must be nonempty and finalized"
        }
        return section
    }

    fun requireUnpublished(repository: String, release: SirenReleaseVersion, token: String,
        status: (String, Map<String, String>) -> Int = ::httpStatus) {
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository)) { "Invalid GitHub repository" }
        val headers = mutableMapOf("Accept" to "application/vnd.github+json")
        if (token.isNotEmpty()) headers["Authorization"] = "Bearer $token"
        val tagStatus = status("https://api.github.com/repos/$repository/git/ref/tags/${release.tag}", headers)
        check(tagStatus == 404) {
            if (tagStatus == 200) "${release.tag} already exists" else "Cannot verify GitHub tag absence (HTTP $tagStatus)"
        }
        val pomStatus = status("$central/$groupPath/siren/${release.version}/siren-${release.version}.pom", emptyMap())
        check(pomStatus == 404) {
            if (pomStatus == 200) "${release.version} already exists on Maven Central" else "Cannot verify Maven Central absence (HTTP $pomStatus)"
        }
    }

    fun publicationPaths(version: String): List<String> {
        require(stable.matches(version)) { "Only stable versions can be verified as a release" }
        return listOf("siren" to "jar", "siren-jvm" to "jar", "siren-android" to "aar",
            "siren-iosarm64" to "klib", "siren-iossimulatorarm64" to "klib").flatMap { (module, extension) ->
            val prefix = "$groupPath/$module/$version/$module-$version"
            buildList {
                add("$prefix.pom"); add("$prefix.pom.asc"); add("$prefix.module"); add("$prefix.module.asc")
                add("$prefix.$extension"); add("$prefix.$extension.asc")
                listOf("sources", "javadoc").forEach { classifier ->
                    add("$prefix-$classifier.jar"); add("$prefix-$classifier.jar.asc")
                }
                if (extension == "klib") {
                    add("$prefix-cinterop-sherpaNative.klib"); add("$prefix-cinterop-sherpaNative.klib.asc")
                    add("$prefix-metadata.jar"); add("$prefix-metadata.jar.asc")
                }
            }
        }
    }

    fun publicationStatus(version: String, status: (String, Map<String, String>) -> Int = ::httpStatus): Map<String, Int> {
        val executor = Executors.newFixedThreadPool(6)
        return try {
            executor.invokeAll(publicationPaths(version).map { path -> Callable { path to status("$central/$path", emptyMap()) } })
                .map { it.get() }.toMap()
        } finally { executor.shutdown() }
    }

    private fun httpStatus(url: String, headers: Map<String, String>): Int {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "Siren-release-guard")
        headers.forEach(connection::setRequestProperty)
        return try { connection.responseCode } finally { connection.disconnect() }
    }
}

@DisableCachingByDefault(because = "Release availability is remote mutable state and must be checked every time")
abstract class ValidateSirenRelease : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val propertiesFile: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val changelogFile: RegularFileProperty
    @get:Input abstract val confirmVersion: Property<String>
    @get:Input abstract val branchRef: Property<String>
    @get:Input abstract val repository: Property<String>
    @get:Input abstract val expectedCommit: Property<String>
    @get:Internal abstract val githubToken: Property<String>
    @get:Internal abstract val githubOutputPath: Property<String>
    @get:OutputFile abstract val notesFile: RegularFileProperty
    @get:OutputFile abstract val metadataFile: RegularFileProperty
    init { outputs.upToDateWhen { false } }

    @TaskAction fun validate() {
        val root = propertiesFile.get().asFile.parentFile
        val ref = branchRef.get().ifBlank { git(root, "symbolic-ref", "--quiet", "HEAD") }
        val release = SirenReleaseChecks.validateVersion(SirenReleaseChecks.configuredVersion(propertiesFile.get().asFile.readText()),
            confirmVersion.get(), ref, git(root, "tag", "--list", "v*").lines())
        SirenReleaseChecks.requireCleanWorkspace(git(root, "status", "--porcelain"))
        val commit = git(root, "rev-parse", "HEAD")
        check(expectedCommit.get().isBlank() || commit == expectedCommit.get()) { "Checkout commit does not match the dispatched GitHub SHA" }
        val section = SirenReleaseChecks.changelogSection(changelogFile.get().asFile.readText(), release.version)
        SirenReleaseChecks.requireUnpublished(repository.get(), release, githubToken.get())
        notesFile.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "Siren ${release.version}\n\nBuilt from main (`$commit`).\n\n```kotlin\nimplementation(\"io.github.debanshu777:siren:${release.version}\")\n```\n\n$section\n")
        val metadata = mapOf("version" to release.version, "tag" to release.tag, "previous_tag" to release.previousTag, "commit" to commit)
        metadataFile.get().asFile.apply { parentFile.mkdirs() }.writeText(GsonBuilder().setPrettyPrinting().create().toJson(metadata) + "\n")
        githubOutputPath.get().takeIf(String::isNotBlank)?.let { output ->
            File(output).appendText(metadata.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
        }
        logger.lifecycle("Validated unpublished Siren ${release.version}; release notes: ${notesFile.get().asFile}")
    }

    private fun git(root: File, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(root).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        check(process.waitFor() == 0) { "Git release-context validation failed: $output" }
        return output
    }
}

@DisableCachingByDefault(because = "Checks delivery and signatures on the public Maven Central repository")
abstract class VerifySirenCentralPublication : DefaultTask() {
    @get:Input abstract val releaseVersion: Property<String>
    @get:Input abstract val attempts: Property<Int>
    @get:Input abstract val pauseSeconds: Property<Int>
    @get:OutputFile abstract val reportFile: RegularFileProperty
    init { outputs.upToDateWhen { false } }

    @TaskAction fun verify() {
        require(attempts.get() > 0 && pauseSeconds.get() >= 0)
        repeat(attempts.get()) { attempt ->
            val statuses = SirenReleaseChecks.publicationStatus(releaseVersion.get())
            val bad = statuses.filterValues { it != 200 && it != 404 }
            val missing = statuses.filterValues { it != 200 }
            reportFile.get().asFile.apply { parentFile.mkdirs() }.writeText(GsonBuilder().setPrettyPrinting().create().toJson(
                mapOf("version" to releaseVersion.get(), "attempt" to attempt + 1, "verified" to missing.isEmpty(), "files" to statuses)) + "\n")
            check(bad.isEmpty()) { "Cannot verify Central delivery: ${bad.entries.first()}" }
            if (missing.isEmpty()) { logger.lifecycle("Verified ${statuses.size} public artifacts/metadata/signatures"); return }
            check(attempt < attempts.get() - 1) { "Central propagation incomplete: ${missing.size} expected files are missing" }
            logger.lifecycle("Waiting for Central propagation: ${missing.size} files missing (${attempt + 1}/${attempts.get()})")
            Thread.sleep(pauseSeconds.get() * 1000L)
        }
    }
}
