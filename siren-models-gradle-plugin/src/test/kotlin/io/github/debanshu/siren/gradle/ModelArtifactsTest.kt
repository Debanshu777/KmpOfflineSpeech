package io.github.debanshu.siren.gradle

import com.google.gson.JsonParser
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ModelArtifactsTest {
    private fun temporary(block: (File) -> Unit) {
        val root = Files.createTempDirectory("speech-setup-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun zip(root: File, entries: List<Pair<String, String>>, symlink: Boolean = false): File {
        val file = root.resolve("test.zip")
        if (!symlink && entries.map { it.first }.distinct().size == entries.size) {
            java.util.zip.ZipOutputStream(file.outputStream()).use { output -> entries.forEach { (name, text) ->
                output.putNextEntry(java.util.zip.ZipEntry(name))
                output.write(text.toByteArray()); output.closeEntry()
            } }
            return file
        }
        ZipArchiveOutputStream(file).use { output -> entries.forEach { (name, text) ->
            val entry = ZipArchiveEntry(name)
            if (symlink) entry.unixMode = 0xA1FF
            output.putArchiveEntry(entry); output.write(text.toByteArray()); output.closeArchiveEntry()
        } }
        return file
    }
    private fun tar(root: File, name: String, link: Boolean = false, hardLink: Boolean = false): File {
        val file = root.resolve("test.tar.bz2")
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { output ->
            val type = if (link) (if (hardLink) '1' else '2').code.toByte() else '0'.code.toByte()
            val entry = TarArchiveEntry(name, type)
            if (link) entry.linkName = "../../outside" else entry.size = 5
            output.putArchiveEntry(entry)
            if (!link) output.write("model".toByteArray())
            output.closeArchiveEntry()
        }
        return file
    }
    private fun artifact(name: String = "model.bin") = ModelArtifact(JsonParser.parseString(
        """{"name":"$name","sha256":"cea23dd4b87e8b00d19fb9ccaaef93e97353c7353e2070f3baf05aeb3995dff4","bytes":8,"url":"https://example.invalid/model.bin"}"""
    ).asJsonObject)

    @Test fun regularZipAndTarPreserveBytes() = temporary { root ->
        ModelArtifacts.extract(zip(root, listOf("Framework.xcframework/ios-arm64/lib.a" to "archive")), root.resolve("zip"))
        assertEquals("archive", root.resolve("zip/Framework.xcframework/ios-arm64/lib.a").readText())
        ModelArtifacts.extract(tar(root, "kitten/model.onnx"), root.resolve("tar"))
        assertEquals("model", root.resolve("tar/kitten/model.onnx").readText())
    }
    @Test fun unsafeZipPathsRejectBeforeWriting() = temporary { root ->
        listOf("../escape", "/escape", "C:/escape", "folder\\escape", ".").forEach { path ->
            val archive = zip(root, listOf("good" to "fine", path to "bad"))
            val output = root.resolve("output")
            assertFailsWith<IllegalArgumentException>(path) { ModelArtifacts.extract(archive, output) }
            assertFalse(output.exists())
        }
    }
    @Test fun linksAreRejectedBeforeWriting() = temporary { root ->
        assertFailsWith<IllegalArgumentException> { ModelArtifacts.extract(zip(root, listOf("link" to "../outside"), true), root.resolve("output")) }
        listOf(false, true).forEach { hard ->
            assertFailsWith<IllegalArgumentException> { ModelArtifacts.extract(tar(root, "link", true, hard), root.resolve("output")) }
        }
        assertFalse(root.resolve("output").exists())
    }
    @Test fun tarTraversalIsRejected() = temporary { root ->
        assertFailsWith<IllegalArgumentException> { ModelArtifacts.extract(tar(root, "../outside"), root.resolve("output")) }
        assertFalse(root.resolve("output").exists())
    }
    @Test fun duplicatesAndFileParentsAreRejected() = temporary { root ->
        listOf(listOf("same" to "a", "same" to "b"), listOf("folder" to "file", "folder/nested" to "bad")).forEach { entries ->
            assertFailsWith<IllegalArgumentException> { ModelArtifacts.extract(zip(root, entries), root.resolve("output")) }
            assertFalse(root.resolve("output").exists())
        }
    }
    @Test fun archiveExpansionCapIsEnforced() = temporary { root ->
        assertFailsWith<IllegalArgumentException> { ModelArtifacts.extract(zip(root, listOf("large" to "12345")), root.resolve("output"), 4) }
        assertFalse(root.resolve("output").exists())
    }
    @Test fun corruptDownloadNeverEntersCache() = temporary { root ->
        val cache = root.resolve("cache")
        assertFailsWith<IllegalArgumentException> { ModelArtifacts.fetch(artifact(), cache, false) { ByteArrayInputStream("modified".toByteArray()) } }
        assertTrue(cache.listFiles()!!.isEmpty())
    }
    @Test fun oversizedDownloadStopsWithoutPublishingCacheBytes() = temporary { root ->
        val cache = root.resolve("cache")
        val error = assertFailsWith<IllegalArgumentException> {
            ModelArtifacts.fetch(artifact(), cache, false) { ByteArrayInputStream("expected!".toByteArray()) }
        }
        assertContains(error.message.orEmpty(), "Oversized model download")
        assertTrue(cache.listFiles()!!.isEmpty())
    }
    @Test fun corruptCacheIsReplacedAndThenWorksOffline() = temporary { root ->
        val cache = root.resolve("cache").apply { mkdirs() }
        val artifact = artifact()
        cache.resolve("${artifact.sha256}-${artifact.name}").writeText("tampered")
        var calls = 0
        val output = ModelArtifacts.fetch(artifact, cache, false) { calls++; ByteArrayInputStream("expected".toByteArray()) }
        assertEquals(1, calls)
        assertEquals("expected", output.readText())
        assertEquals(output, ModelArtifacts.fetch(artifact, cache, true) { fail("Offline cache must avoid network") })
    }
    @Test fun offlineMissAndInvalidManifestFailClearly() = temporary { root ->
        assertFailsWith<IllegalStateException> { ModelArtifacts.fetch(artifact(), root.resolve("cache"), true) { fail("Offline must avoid network") } }
        val manifest = root.resolve("manifest.json").apply { writeText("""{"schema":2}""") }
        assertFailsWith<IllegalArgumentException> { ModelArtifacts.manifest(manifest) }
        manifest.writeText("malformed")
        assertFails { ModelArtifacts.manifest(manifest) }
    }
    @Test fun failedPublishLeavesExistingOutputIntact() = temporary { root ->
        val source = root.resolve("new").apply { mkdirs(); resolve("model").writeText("new") }
        val output = root.resolve("models").apply { mkdirs(); resolve("model").writeText("old") }
        root.resolve(".models-previous").mkdirs()
        assertFailsWith<IllegalStateException> { ModelArtifacts.publish(source, output) }
        assertEquals("old", output.resolve("model").readText())
    }
}
