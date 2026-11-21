import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

data class SpeechArtifact(val json: JsonObject) {
    val name: String get() = json["name"].asString
    val sha256: String get() = json["sha256"].asString
    val size: Long? get() = json["bytes"]?.asLong
    val destination: String get() = json["destination"].asString
}

object SpeechArtifacts {
    const val MAX_EXTRACT_BYTES = 3L * 1024 * 1024 * 1024
    fun manifest(file: File): JsonObject = JsonParser.parseString(file.readText()).asJsonObject.also {
        require(it["schema"].asInt == 1) { "Unsupported speech manifest schema" }
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(file: File, artifact: SpeechArtifact) {
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Missing regular file: ${file.name}" }
        require(artifact.size == null || file.length() == artifact.size) { "Size mismatch: ${file.name}" }
        require(sha256(file) == artifact.sha256) { "SHA-256 mismatch: ${file.name}" }
    }
    fun fetch(artifact: SpeechArtifact, cache: File, offline: Boolean, open: (String) -> InputStream = ::openUrl): File {
        require(artifact.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid SHA-256 pin" }
        require(relative(artifact.name).nameCount == 1) { "Artifact name must be a filename" }
        cache.mkdirs()
        val output = cache.resolve("${artifact.sha256}-${artifact.name}")
        if (output.exists()) {
            try { verify(output, artifact); return output } catch (_: IllegalArgumentException) { output.delete() }
        }
        check(!offline) { "Missing verified cache entry ${artifact.name}; run once without --offline" }
        val temporary = Files.createTempFile(cache.toPath(), ".speech-", ".part").toFile()
        try {
            open(artifact.json["url"].asString).use { input -> temporary.outputStream().use { input.copyTo(it) } }
            verify(temporary, artifact)
            move(temporary.toPath(), output.toPath())
            return output
        } finally { temporary.delete() }
    }
    private fun openUrl(url: String): InputStream {
        require(URI(url).scheme == "https") { "Speech downloads must use HTTPS" }
        return URI(url).toURL().openConnection().apply {
            connectTimeout = 30_000; readTimeout = 120_000
            setRequestProperty("User-Agent", "kmp-offline-speech-gradle/1")
        }.getInputStream()
    }
    fun relative(name: String): Path {
        require(name.isNotEmpty() && '\\' !in name && ':' !in name && !name.startsWith('/')) { "Unsafe archive path: $name" }
        val path = Path.of(name.trimEnd('/'))
        require(path.toString().isNotEmpty() && path.none { it.toString() == ".." } && path.normalize().toString().isNotEmpty() && path.normalize().toString() != ".") { "Unsafe archive path: $name" }
        return path.normalize()
    }
    private data class Entry(val name: String, val path: Path, val directory: Boolean, val size: Long)
    fun extract(archive: File, output: File, limit: Long = MAX_EXTRACT_BYTES) {
        require(!output.exists() && !Files.isSymbolicLink(output.toPath())) { "Extraction destination must be new" }
        val zip = archive.name.endsWith(".zip")
        val entries = mutableListOf<Entry>()
        if (zip) ZipFile.builder().setFile(archive).get().use { source ->
            source.entries.asSequence().forEach { entry ->
                // Commons Compress may normalize FAT backslashes in `name`.
                // Validate the original bytes as well, before that normalization.
                relative(String(entry.rawName, Charsets.UTF_8))
                val type = entry.unixMode and 0xF000
                require(!entry.isUnixSymlink && type in setOf(0, 0x8000, 0x4000)) { "Archive link or special file: ${entry.name}" }
                entries += Entry(entry.name, relative(entry.name), entry.isDirectory, entry.size)
            }
        } else tar(archive).use { source ->
            while (true) {
                val entry = source.nextEntry ?: break
                require(entry.isDirectory || entry.isFile && !entry.isLink && !entry.isSymbolicLink && !entry.isSparse) { "Archive link or special file: ${entry.name}" }
                entries += Entry(entry.name, relative(entry.name), entry.isDirectory, entry.size)
            }
        }
        val seen = mutableSetOf<Path>()
        val files = entries.filterNot { it.directory }.map { it.path }.toSet()
        var total = 0L
        entries.forEach { entry ->
            require(seen.add(entry.path)) { "Duplicate archive path: ${entry.name}" }
            require(entry.size >= 0 && entry.size <= limit - total) { "Archive exceeds extraction size limit" }
            total += entry.size
            var parent = entry.path.parent
            while (parent != null) { require(parent !in files) { "Archive file/directory conflict" }; parent = parent.parent }
        }
        output.mkdirs()
        fun write(entry: Entry, input: InputStream?) {
            val target = output.toPath().resolve(entry.path)
            if (entry.directory) { Files.createDirectories(target); return }
            requireNotNull(input)
            Files.createDirectories(target.parent)
            Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW).use { stream ->
                val buffer = ByteArray(64 * 1024)
                var count = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(n.toLong() <= entry.size - count) { "Oversized archive payload: ${entry.name}" }
                    stream.write(buffer, 0, n)
                    count += n
                }
                require(count == entry.size) { "Truncated archive payload: ${entry.name}" }
            }
        }
        if (zip) ZipFile.builder().setFile(archive).get().use { source ->
            entries.forEach { entry ->
                if (entry.directory) write(entry, null)
                else source.getInputStream(source.getEntry(entry.name)).use { write(entry, it) }
            }
        } else tar(archive).use { source ->
            entries.forEach { entry -> check(source.nextEntry?.name == entry.name); write(entry, source) }
        }
    }
    private fun tar(file: File) = TarArchiveInputStream(BZip2CompressorInputStream(file.inputStream().buffered()))
    fun copyFile(source: File, target: File) {
        target.parentFile.mkdirs()
        require(!Files.isSymbolicLink(target.toPath())) { "Refusing output symlink: $target" }
        val temporary = Files.createTempFile(target.parentFile.toPath(), ".speech-", ".tmp")
        try { Files.copy(source.toPath(), temporary, REPLACE_EXISTING); move(temporary, target.toPath()) }
        finally { Files.deleteIfExists(temporary) }
    }
    fun publish(source: File, output: File) {
        output.parentFile.mkdirs()
        require(!Files.isSymbolicLink(output.toPath())) { "Refusing output symlink: $output" }
        val stage = Files.createTempDirectory(output.parentFile.toPath(), ".speech-stage-").toFile()
        val backup = File(output.parentFile, ".${output.name}-previous")
        try {
            source.copyRecursively(stage, overwrite = true)
            check(!backup.exists()) { "Recover or remove interrupted output backup: $backup" }
            if (output.exists()) move(output.toPath(), backup.toPath())
            try { move(stage.toPath(), output.toPath()) }
            catch (error: Exception) { if (backup.exists()) move(backup.toPath(), output.toPath()); throw error }
            backup.deleteRecursively()
        } finally { stage.deleteRecursively() }
    }
    private fun move(source: Path, destination: Path) {
        try { Files.move(source, destination, ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source, destination, REPLACE_EXISTING) }
    }
}
