import java.io.File

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

val repositoryPath = providers.gradleProperty("sirenRepository").orNull
val sirenRepository = repositoryPath?.let { File(it).also { repository ->
    require(repository.isAbsolute && repository.isDirectory) { "sirenRepository must be an existing absolute directory" }
} }
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                if (sirenRepository != null) {
                    maven { name = "sirenStagedArtifacts"; url = uri(sirenRepository) }
                } else {
                    mavenCentral()
                }
            }
            filter { includeGroup("io.github.debanshu777") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "SirenPublishedConsumer"
include(":androidApp")
