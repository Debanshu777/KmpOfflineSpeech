import kotlin.test.*

class ModelAddonReleaseTest {
    @Test fun stableVersionRequiresExactConfirmationAndMain() {
        assertEquals("models-v0.1.0", ModelAddonRelease.version("0.1.0", "0.1.0", "refs/heads/main"))
        listOf("0.1.0-SNAPSHOT", "01.1.0", "1.0").forEach { version ->
            assertFailsWith<IllegalArgumentException> { ModelAddonRelease.version(version, version, "refs/heads/main") }
        }
        assertFailsWith<IllegalArgumentException> { ModelAddonRelease.version("0.1.0", "0.1.1", "refs/heads/main") }
        assertFailsWith<IllegalArgumentException> { ModelAddonRelease.version("0.1.0", "0.1.0", "refs/heads/feature") }
    }
    @Test fun deliveryIncludesMarkerAndImplementationSignatures() {
        val paths = ModelAddonRelease.paths("0.1.0")
        assertEquals(12, paths.size)
        assertEquals(paths.size, paths.distinct().size)
        assertTrue(paths.any { it.endsWith("gradle.plugin-0.1.0.pom.asc") })
        assertTrue(paths.any { it.endsWith("-javadoc.jar.asc") })
        assertTrue(paths.any { it.endsWith("-sources.jar.asc") })
        assertTrue(paths.all { it.startsWith("io/github/debanshu777/") && ".." !in it })
    }
    @Test fun notesAreIndependentOfTheCoreRelease() {
        val text = "## [0.1.0]\nCore\n\n## [Models 0.1.0]\nModel release\n\n## [Older]\nOther\n"
        assertEquals("Model release", ModelAddonRelease.notes(text, "0.1.0"))
        assertFailsWith<IllegalArgumentException> { ModelAddonRelease.notes("## [Models 0.1.0]\nTBD", "0.1.0") }
        assertFailsWith<IllegalArgumentException> { ModelAddonRelease.notes(text + "\n## [Models 0.1.0]\nDuplicate", "0.1.0") }
        assertFailsWith<IllegalArgumentException> { ModelAddonRelease.notes("## [0.1.0]\nCore", "0.1.0") }
    }
}
