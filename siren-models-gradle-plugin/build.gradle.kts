import java.util.concurrent.Callable
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.4.21"
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("org.jetbrains.dokka") version "2.2.0"
    signing
}

group = "io.github.debanshu777"
version = providers.gradleProperty("sirenModelsVersion").get()
kotlin { jvmToolchain(21) }

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    compileOnly("com.android.tools.build:gradle-api:9.5.1")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.apache.commons:commons-compress:1.28.0")
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins.create("sirenModels") {
        id = "io.github.debanshu777.siren.models"
        displayName = "Siren quick-start SDK and models"
        description = "Set up Siren with verified default model packaging and a whole-bundle opt-out"
        implementationClass = "io.github.debanshu.siren.gradle.SirenModelsPlugin"
    }
}

// The published plugin carries only a tiny pinned catalog, never the weight files.
val catalogFile = layout.projectDirectory.file("../scripts/dependencies.json")
val generatedResources = layout.buildDirectory.dir("generated/catalog")
val generateModelCatalog = tasks.register("generateModelCatalog") {
    inputs.file(catalogFile)
    outputs.dir(generatedResources)
    doLast {
        val manifest = JsonSlurper().parse(catalogFile.asFile) as Map<*, *>
        val output = generatedResources.get().file("siren-models/catalog.json").asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("schema" to 1, "models" to manifest["models"]))) + "\n")
    }
}
sourceSets.main { resources.srcDir(generateModelCatalog) }
tasks.processResources { dependsOn(generateModelCatalog) }

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("Siren quick-start SDK and models")
        description.set("Build-time Siren SDK setup and verified default model packaging with an opt-out")
        url.set("https://github.com/Debanshu777/Siren")
        inceptionYear.set("2026")
        licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
        developers { developer { id.set("debanshu777"); name.set("Debanshu Datta"); url.set("https://github.com/Debanshu777") } }
        scm {
            url.set("https://github.com/Debanshu777/Siren")
            connection.set("scm:git:https://github.com/Debanshu777/Siren.git")
            developerConnection.set("scm:git:ssh://git@github.com/Debanshu777/Siren.git")
        }
    }
}
signing {
    setRequired(Callable { gradle.taskGraph.allTasks.any { it.name.contains("MavenCentral", ignoreCase = true) } })
}
publishing {
    repositories { maven { name = "staging"; url = uri(layout.buildDirectory.dir("staging-repository")) } }
}

apply(from = "release.gradle.kts")
