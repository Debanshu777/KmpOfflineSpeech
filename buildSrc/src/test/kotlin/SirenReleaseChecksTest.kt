import java.io.IOException
import java.io.File
import kotlin.test.*

class SirenReleaseChecksTest {
    @Test fun dirtyReleaseSourceIsRejected() {
        SirenReleaseChecks.requireCleanWorkspace("")
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.requireCleanWorkspace(" M siren/src/File.kt") }
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.requireCleanWorkspace("?? unreviewed.txt") }
    }

    @Test fun configuredVersionMustBeUnique() {
        assertEquals("0.1.0", SirenReleaseChecks.configuredVersion("# release\nsirenVersion=0.1.0\n"))
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.configuredVersion("otherVersion=0.1.0") }
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.configuredVersion("sirenVersion=0.1.0\nsirenVersion=0.2.0") }
    }

    @Test fun stableReleaseRequiresMainExactConfirmationAndForwardVersion() {
        fun validate(version: String = "0.1.0", confirmed: String = version, ref: String = "refs/heads/main", tags: List<String> = emptyList()) =
            SirenReleaseChecks.validateVersion(version, confirmed, ref, tags)
        assertEquals(SirenReleaseVersion("0.1.0", "v0.1.0", ""), validate())
        assertFailsWith<IllegalArgumentException> { validate(ref = "refs/heads/feature") }
        assertFailsWith<IllegalArgumentException> { validate(confirmed = "0.2.0") }
        assertFailsWith<IllegalArgumentException> { validate("0.1.0-SNAPSHOT") }
        assertFailsWith<IllegalArgumentException> { validate("00.1.0") }
        assertFailsWith<IllegalArgumentException> { validate(tags = listOf("v0.1.0")) }
        assertFailsWith<IllegalArgumentException> { validate(tags = listOf("v0.2.0")) }
        assertEquals("v0.10.0", validate("0.11.0", tags = listOf("v0.9.0", "v0.10.0")).previousTag)
    }

    @Test fun changelogSelectsOnlyOneFinalizedVersionSection() {
        assertEquals("- First release.", SirenReleaseChecks.changelogSection("# Changes\n## [0.1.0] - date\n- First release.\n## [0.0.1]\n- Old.", "0.1.0"))
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.changelogSection("## Unreleased\n- Work", "0.1.0") }
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.changelogSection("## 0.1.0\nTODO", "0.1.0") }
        assertFailsWith<IllegalArgumentException> { SirenReleaseChecks.changelogSection("## 0.1.0\n- A\n## 0.1.0\n- B", "0.1.0") }
    }

    @Test fun absenceGuardFailsClosedAndDoesNotSendGithubTokenToCentral() {
        val release = SirenReleaseVersion("0.1.0", "v0.1.0", "")
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        SirenReleaseChecks.requireUnpublished("Debanshu777/Siren", release, "test-token") { url, headers -> calls += url to headers; 404 }
        assertEquals("Bearer test-token", calls[0].second["Authorization"])
        assertTrue(calls[1].second.isEmpty())
        assertFailsWith<IllegalStateException> { SirenReleaseChecks.requireUnpublished("Debanshu777/Siren", release, "") { _, _ -> 200 } }
        assertFailsWith<IllegalStateException> { SirenReleaseChecks.requireUnpublished("Debanshu777/Siren", release, "") { _, _ -> 403 } }
        var call = 0
        assertFailsWith<IllegalStateException> { SirenReleaseChecks.requireUnpublished("Debanshu777/Siren", release, "") { _, _ -> if (++call == 1) 404 else 200 } }
        assertFailsWith<IOException> { SirenReleaseChecks.requireUnpublished("Debanshu777/Siren", release, "") { _, _ -> throw IOException("offline") } }
    }

    @Test fun publicationDeliveryIncludesAllTargetsNativeClassifiersAndSignatures() {
        val paths = SirenReleaseChecks.publicationPaths("0.1.0")
        assertEquals(58, paths.size)
        assertEquals(paths.size, paths.distinct().size)
        assertEquals(2, paths.count { it.endsWith("-cinterop-sherpaNative.klib.asc") })
        assertEquals(5, paths.count { it.endsWith("-sources.jar.asc") })
        assertEquals(5, paths.count { it.endsWith("-javadoc.jar.asc") })
        assertEquals(5, paths.count { it.endsWith(".module.asc") })
        assertEquals(2, paths.count { it.endsWith("-metadata.jar.asc") })
        assertTrue(paths.any { "siren-android" in it && it.endsWith(".aar.asc") })
        assertTrue(SirenReleaseChecks.publicationStatus("0.1.0") { _, _ -> 200 }.values.all { it == 200 })
        assertTrue(SirenReleaseChecks.publicationStatus("0.1.0") { _, _ -> 404 }.values.all { it == 404 })
    }

    @Test fun centralConsumerHasNoStagingRepositoryAndRefreshesRemoteResolution() {
        val wrapper = File("/tmp/consumer/gradlew")
        val models = File("/tmp/consumer/models")
        val central = SirenConsumerInvocation.arguments(wrapper, null, "0.1.0", models)
        assertTrue("--refresh-dependencies" in central)
        assertFalse("--offline" in central)
        assertFalse(central.any { it.startsWith("-PsirenRepository=") })
        val staged = SirenConsumerInvocation.arguments(wrapper, File("/tmp/repository"), "0.1.0", models)
        assertFalse("--offline" in staged)
        assertFalse("--refresh-dependencies" in staged)
        assertTrue("-PsirenRepository=/tmp/repository" in staged)
        assertTrue("-Pnative=true" in central)
        val offlineStaged = SirenConsumerInvocation.arguments(wrapper, File("/tmp/repository"), "0.1.0", models, offline = true)
        assertTrue("--offline" in offlineStaged)
        assertTrue("-PsirenRepository=/tmp/repository" in offlineStaged)
        assertFailsWith<IllegalArgumentException> {
            SirenConsumerInvocation.arguments(wrapper, null, "0.1.0", models, offline = true)
        }
    }
}
