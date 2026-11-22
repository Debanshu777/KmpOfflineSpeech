import java.io.File

pluginManagement {
    val modelPluginRepository = providers.gradleProperty("modelPluginRepository").orNull
    if (modelPluginRepository == null) includeBuild("../../siren-models-gradle-plugin")
    repositories {
        if (modelPluginRepository != null) maven { url = uri(modelPluginRepository) }
        google(); mavenCentral(); gradlePluginPortal()
    }
    plugins { id("io.github.debanshu777.siren.models") version "0.1.0" }
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
rootProject.name = "SirenModelsConsumer"
include(":androidApp")
