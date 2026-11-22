import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*

class NativeRedistributionNoticesTest {
    @Test fun vendoredNoticesArePinnedAndCannotEscapeTheirDirectory() {
        val root = Files.createTempDirectory("siren-vendored-notices-").toFile()
        try {
            val file = root.resolve("scripts/notices/compiler/NOTICE").apply { parentFile.mkdirs(); writeText("Exact compiler license") }
            val json = com.google.gson.JsonObject().apply {
                addProperty("name", "compiler-NOTICE")
                addProperty("source_path", "scripts/notices/compiler/NOTICE")
                addProperty("sha256", SpeechArtifacts.sha256(file))
                addProperty("bytes", file.length())
            }
            assertEquals(file, SpeechArtifacts.vendoredNotice(SpeechArtifact(json), root))
            file.appendText("tampered")
            assertFailsWith<IllegalArgumentException> { SpeechArtifacts.vendoredNotice(SpeechArtifact(json), root) }
            json.addProperty("source_path", "scripts/notices/../../outside")
            assertFailsWith<IllegalArgumentException> { SpeechArtifacts.vendoredNotice(SpeechArtifact(json), root) }
            json.addProperty("source_path", "build/NOTICE")
            assertFailsWith<IllegalArgumentException> { SpeechArtifacts.vendoredNotice(SpeechArtifact(json), root) }
        } finally { root.deleteRecursively() }
    }

    @Test fun vendoredNoticeSymlinkCannotReadOutsideItsDirectory() {
        val root = Files.createTempDirectory("siren-vendored-symlink-").toFile()
        try {
            val source = root.resolve("outside.txt").apply { writeText("Not a notice") }
            val link = root.resolve("scripts/notices/link").apply { parentFile.mkdirs() }
            Files.createSymbolicLink(link.toPath(), source.toPath())
            val json = com.google.gson.JsonObject().apply {
                addProperty("name", "compiler-NOTICE")
                addProperty("source_path", "scripts/notices/link")
                addProperty("sha256", SpeechArtifacts.sha256(source))
            }
            assertFailsWith<IllegalArgumentException> { SpeechArtifacts.vendoredNotice(SpeechArtifact(json), root) }
        } finally { root.deleteRecursively() }
    }

    @Test fun klibEmbeddingPreservesNativeBytesAndUpdatesNoticesWithoutDuplicates() {
        val directory = Files.createTempDirectory("siren-klib-notices-").toFile()
        try {
            val klib = directory.resolve("native.klib")
            val nativeBytes = ByteArray(4096) { (it % 251).toByte() }
            ZipOutputStream(klib.outputStream()).use { zip ->
                mapOf("default/manifest" to "unique_name=test".toByteArray(), "default/targets/ios_arm64/included/libengine.a" to nativeBytes).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
            val notices = directory.resolve("notices")
            val license = notices.resolve("META-INF/siren/licenses/engine/LICENSE").apply { parentFile.mkdirs(); writeText("License one") }
            NativeRedistributionNotices.embedInKlib(klib, notices)
            license.writeText("License two")
            NativeRedistributionNotices.embedInKlib(klib, notices)
            ZipFile(klib).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(entries.size, entries.map { it.name }.distinct().size)
                assertContentEquals(nativeBytes, zip.getInputStream(zip.getEntry("default/targets/ios_arm64/included/libengine.a")).readBytes())
                assertEquals("License two", zip.getInputStream(zip.getEntry("default/resources/META-INF/siren/licenses/engine/LICENSE")).bufferedReader().readText())
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun unpackagedKlibEmbeddingPreservesArchivesAndManifest() {
        val directory = Files.createTempDirectory("siren-unpacked-klib-").toFile()
        try {
            val klib = directory.resolve("native.klib")
            val manifest = klib.resolve("default/manifest").apply { parentFile.mkdirs(); writeText("unique_name=test") }
            val archive = klib.resolve("default/targets/ios_arm64/included/libengine.a").apply {
                parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val notices = directory.resolve("notices")
            notices.resolve("META-INF/siren/licenses/LICENSE").apply { parentFile.mkdirs(); writeText("Native license") }
            NativeRedistributionNotices.embedInKlib(klib, notices)
            assertEquals("unique_name=test", manifest.readText())
            assertContentEquals(byteArrayOf(1, 2, 3, 4), archive.readBytes())
            assertEquals("Native license", klib.resolve("default/resources/META-INF/siren/licenses/LICENSE").readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun incompleteAuditCannotBeWaivedByChangingOnlyOverallStatus() {
        val manifest = SpeechArtifacts.manifest(java.io.File("../scripts/dependencies.json"))
        val audit = manifest["redistribution"].asJsonObject
        audit.addProperty("status", "complete")
        val component = audit["components"].asJsonArray.first { it.asJsonObject["id"].asString == "onnxruntime-1.28.2" }.asJsonObject
        component.addProperty("status", "incomplete")
        component.add("notice_destinations", com.google.gson.JsonArray())
        component.remove("source_revision")
        component.remove("source_archive_sha256")
        val blockers = NativeRedistributionNotices.blockers(manifest)
        assertTrue(blockers.any { it.startsWith("Incomplete component review: onnxruntime") })
        assertTrue(blockers.any { it.startsWith("Missing pinned component notices: onnxruntime") })
        assertTrue(blockers.any { it.startsWith("Missing immutable source identity: onnxruntime") })
    }

    @Test fun compilerEvidenceAndNoticesCannotBeWaivedByOverallStatus() {
        val manifest = SpeechArtifacts.manifest(java.io.File("../scripts/dependencies.json"))
        val audit = manifest["redistribution"].asJsonObject
        audit.addProperty("status", "complete")
        val review = audit["compiler_runtimes"].asJsonArray.first().asJsonObject
        review.add("notice_destinations", com.google.gson.JsonArray())
        assertTrue(NativeRedistributionNotices.blockers(manifest).any { it.startsWith("Incomplete compiler-runtime evidence/notices") })
        audit.add("compiler_runtimes", com.google.gson.JsonArray())
        assertTrue(NativeRedistributionNotices.blockers(manifest).any { it.startsWith("Compiler-runtime reviews must cover") })
    }
}
