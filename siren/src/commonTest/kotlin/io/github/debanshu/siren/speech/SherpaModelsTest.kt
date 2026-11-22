package io.github.debanshu.siren.speech

import kotlin.test.*

class SherpaModelsTest {
    @Test fun bundledPresetDescribesTheSevenKnownFilesWithoutLoadingThem() {
        val preset = SherpaModels.bundled()
        assertEquals(listOf(
            "models/whisper/tiny-encoder.int8.onnx",
            "models/whisper/tiny-decoder.int8.onnx",
            "models/whisper/tiny-tokens.txt",
            "models/kitten/model.int8.onnx",
            "models/kitten/voices.bin",
            "models/kitten/tokens.txt",
            "models/kitten/lexicon-en-us.txt",
        ), files(preset).map { it.path })
        assertTrue(files(preset).all { it is ModelFile.Bundled })
        assertEquals("Whisper tiny", preset.recognition!!.displayName)
        assertEquals("Kitten nano · English", preset.synthesis!!.displayName)
        assertEquals(2, preset.threads)
    }

    @Test fun bundledDirectorySupportsNestedPathsTrailingSlashesAndTheResourceRoot() {
        assertEquals("assets/speech/whisper/tiny-encoder.int8.onnx",
            SherpaModels.bundled("assets/speech///").recognition!!.encoder.path)
        assertEquals("whisper/tiny-encoder.int8.onnx", SherpaModels.bundled("").recognition!!.encoder.path)
    }

    @Test fun bundledDirectoryRejectsTraversalAndAbsolutePathsEvenWithoutCapabilities() {
        listOf("/", "/models", "//server/models", "C:/models", "C:\\models", "C:models",
            "\\\\server\\models", "../models", "models/../other", "models/./other", "models//other",
            "models\\other", " ").forEach { directory ->
            assertFailsWith<IllegalArgumentException>(directory) {
                SherpaModels.bundled(directory, recognition = false, synthesis = false)
            }
        }
    }

    @Test fun localDirectoryPreservesUnixAndWindowsFilesystemRoots() {
        assertEquals("/whisper/tiny-encoder.int8.onnx", SherpaModels.local("/").recognition!!.encoder.path)
        assertEquals("/opt/speech/kitten/model.int8.onnx", SherpaModels.local("/opt/speech/").synthesis!!.model.path)
        assertEquals("C:\\whisper\\tiny-encoder.int8.onnx", SherpaModels.local("C:\\").recognition!!.encoder.path)
        assertEquals("C:/kitten/voices.bin", SherpaModels.local("C:/").synthesis!!.voices.path)
        assertEquals("\\\\server\\share\\speech\\kitten\\tokens.txt",
            SherpaModels.local("\\\\server\\share\\speech").synthesis!!.tokens.path)
        assertTrue(files(SherpaModels.local("/opt/speech")).all { it is ModelFile.Local })
    }

    @Test fun capabilityFlagsAreIndependentAndThreadsStillValidate() {
        val recognitionOnly = SherpaModels.bundled(synthesis = false, threads = 4)
        assertNotNull(recognitionOnly.recognition)
        assertNull(recognitionOnly.synthesis)
        assertEquals(4, recognitionOnly.threads)
        val synthesisOnly = SherpaModels.local("/models", recognition = false)
        assertNull(synthesisOnly.recognition)
        assertNotNull(synthesisOnly.synthesis)
        val neither = SherpaModels.bundled(recognition = false, synthesis = false)
        assertNull(neither.recognition)
        assertNull(neither.synthesis)
        assertFailsWith<IllegalArgumentException> { SherpaModels.bundled(threads = 0) }
        assertFailsWith<IllegalArgumentException> { SherpaModels.local("/models", threads = -1) }
    }

    @Test fun localOnlyRejectsBlankDirectoryBeforePlatformResolution() {
        assertFailsWith<IllegalArgumentException> { SherpaModels.local("") }
        assertFailsWith<IllegalArgumentException> { SherpaModels.local(" \t", recognition = false, synthesis = false) }
        assertEquals("relative/whisper/tiny-tokens.txt", SherpaModels.local("relative").recognition!!.tokens.path)
    }

    private fun files(models: SherpaModels): List<ModelFile> = listOfNotNull(
        models.recognition?.encoder, models.recognition?.decoder, models.recognition?.tokens,
        models.synthesis?.model, models.synthesis?.voices, models.synthesis?.tokens, models.synthesis?.lexicon,
    )
}
