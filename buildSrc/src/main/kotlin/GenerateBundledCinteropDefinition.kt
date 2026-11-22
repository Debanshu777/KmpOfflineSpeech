import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.util.Properties

/** Embed native archive bytes in the generated cinterop KLIB, not checkout linker paths. */
@DisableCachingByDefault(because = "Generated definitions contain the local archive search directory")
abstract class GenerateBundledCinteropDefinition : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templateFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val staticArchives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sources = staticArchives.files.sortedBy { it.name }
        require(sources.isNotEmpty()) { "At least one static archive is required" }
        require(sources.all { it.isFile && it.length() > 0L }) { "A prepared static archive is missing or empty" }
        val names = sources.map { if (it.name.endsWith(".a")) it.name else "lib${it.name}.a" }
        require(names.distinct().size == names.size && names.all { it.matches(Regex("[A-Za-z0-9_.-]+")) }) {
            "Static archive names must be unique filenames"
        }
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        check(output.mkdirs()) { "Could not create cinterop output: $output" }
        sources.zip(names).forEach { (source, name) -> source.copyTo(output.resolve(name)) }
        val properties = Properties().apply {
            templateFile.get().asFile.inputStream().use { load(it) }
            setProperty("staticLibraries", names.joinToString(" "))
            // .def uses both Java-properties escaping and quoted argument parsing.
            val quotedPath = "\"" + output.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            setProperty("libraryPaths", quotedPath)
        }
        val serialized = ByteArrayOutputStream().also { properties.store(it, null) }
            .toString(Charsets.ISO_8859_1.name())
            .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.sorted()
            .joinToString("\n", postfix = "\n")
        // Write only after every archive has been copied; omit Properties.store's timestamp.
        output.resolve("interop.def").writeText(serialized, Charsets.ISO_8859_1)
    }
}
