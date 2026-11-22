package io.github.debanshu.siren.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.*

class SirenModelsPluginTest {
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("siren-model-plugin-").toFile()
        try {
            root.resolve("settings.gradle.kts").writeText("rootProject.name = \"model-consumer\"\n")
            root.resolve("build.gradle.kts").writeText("""
                plugins { java; id("io.github.debanshu777.siren.models") }
                sirenModels { recognition.set(false); synthesis.set(false) }
            """.trimIndent())
            block(root)
        } finally { root.deleteRecursively() }
    }
    private fun runner(root: File, vararg tasks: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root).withPluginClasspath()
        .withArguments(*tasks, "--offline", "--configuration-cache", "--stacktrace")

    @Test fun configurationDoesNotDownloadModelsAndReusesCache() = fixture { root ->
        runner(root, "help").build()
        assertFalse(root.resolve(".gradle/siren-models-cache").exists())
        assertFalse(root.resolve("build/generated/siren-models").exists())
        assertContains(runner(root, "help").build().output, "Reusing configuration cache")
    }

    @Test fun javaResourcesAndIosStagingUseGeneratedOutputs() = fixture { root ->
        runner(root, "jar", "copySirenModelsForXcode").build()
        JarFile(root.resolve("build/libs/model-consumer.jar")).use { jar ->
            assertNotNull(jar.getJarEntry("models/SIREN-MODELS-NOTICE.txt"))
            assertFalse(jar.entries().asSequence().any { it.name.endsWith(".onnx") })
        }
        assertTrue(root.resolve("build/generated/siren-models/ios/models/SIREN-MODELS-NOTICE.txt").isFile)
        val again = runner(root, "jar", "copySirenModelsForXcode").build()
        assertEquals(TaskOutcome.UP_TO_DATE, again.task(":prepareSirenModels")!!.outcome)
        assertContains(again.output, "Reusing configuration cache")
    }

    @Test fun xcodeCopiesOnlyModelsAndPreservesOtherAppResources() = fixture { root ->
        val target = root.resolve("xcode").apply { mkdirs() }
        val app = target.resolve("Consumer.app").apply { mkdirs() }
        app.resolve("keep.txt").writeText("application-owned")
        val environment = System.getenv() + mapOf("TARGET_BUILD_DIR" to target.absolutePath,
            "UNLOCALIZED_RESOURCES_FOLDER_PATH" to "Consumer.app")
        root.resolve("build.gradle.kts").appendText("\ntasks.register(\"embedAndSignAppleFrameworkForXcode\")\n")
        runner(root, "embedAndSignAppleFrameworkForXcode").withEnvironment(environment).build()
        assertEquals("application-owned", app.resolve("keep.txt").readText())
        assertTrue(app.resolve("models/SIREN-MODELS-NOTICE.txt").isFile)
        assertFalse(app.resolve("models/models").exists())
    }

    @Test fun xcodeTraversalFailsWithoutWritingOutsideTheApp() = fixture { root ->
        val environment = System.getenv() + mapOf("TARGET_BUILD_DIR" to root.resolve("xcode").absolutePath,
            "UNLOCALIZED_RESOURCES_FOLDER_PATH" to "../Outside.app")
        val result = runner(root, "copySirenModelsForXcode").withEnvironment(environment).buildAndFail()
        assertContains(result.output, "Unsafe archive path")
        assertFalse(root.resolve("Outside.app").exists())
    }

    @Test fun offlineMissingModelLeavesPreviousPublishedAssetsIntact() = fixture { root ->
        runner(root, "prepareSirenModels").build()
        val notice = root.resolve("build/generated/siren-models/assets/models/SIREN-MODELS-NOTICE.txt")
        val original = notice.readText()
        val build = root.resolve("build.gradle.kts")
        build.writeText(build.readText().replace("recognition.set(false)", "recognition.set(true)"))
        val result = runner(root, "prepareSirenModels").buildAndFail()
        assertContains(result.output, "Missing verified cache entry")
        assertEquals(original, notice.readText())
        assertFalse(notice.parentFile.resolve("whisper").exists())
    }
}
