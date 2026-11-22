import java.io.File

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

val repositoryPath = providers.gradleProperty("sirenRepository").orNull
    ?: error("Pass -PsirenRepository=/absolute/path/to/the/copied/staging-repository")
val sirenRepository = File(repositoryPath)
require(sirenRepository.isAbsolute && sirenRepository.isDirectory) {
    "sirenRepository must be an existing absolute directory"
}
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven { name = "sirenStagedArtifacts"; url = uri(sirenRepository) }
            }
            filter { includeGroup("io.github.debanshu777") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "SirenPublishedConsumer"
include(":androidApp")
