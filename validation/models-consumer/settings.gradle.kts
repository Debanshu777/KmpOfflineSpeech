import java.io.File

pluginManagement {
    val modelPluginRepository = providers.gradleProperty("modelPluginRepository").orNull
    val fromCentral = providers.gradleProperty("modelPluginFromCentral").map(String::toBoolean).getOrElse(false)
    require(!fromCentral || modelPluginRepository == null) { "Central mode cannot use a staged plugin repository" }
    if (!fromCentral && modelPluginRepository == null) includeBuild("../../siren-models-gradle-plugin")
    repositories {
        if (fromCentral || modelPluginRepository != null) exclusiveContent {
            forRepository {
                if (fromCentral) mavenCentral()
                else maven { url = uri(checkNotNull(modelPluginRepository)) }
            }
            filter { includeGroup("io.github.debanshu777"); includeGroup("io.github.debanshu777.siren.models") }
        }
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
