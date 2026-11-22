import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.4.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.debanshu777"
version = "0.1.0"
kotlin { jvmToolchain(21) }
java { withSourcesJar(); withJavadocJar() }
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
        displayName = "Siren optional model packaging"
        description = "Verify and package optional Whisper/Kitten models for Android, JVM and iOS apps"
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

publishing {
    repositories { maven { name = "staging"; url = uri(layout.buildDirectory.dir("staging-repository")) } }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Siren optional model packaging")
            description.set("Build-time model acquisition and packaging; no weights or runtime downloads in the plugin")
            url.set("https://github.com/Debanshu777/Siren")
            licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            developers { developer { id.set("debanshu777"); name.set("Debanshu Datta") } }
            scm { url.set("https://github.com/Debanshu777/Siren"); connection.set("scm:git:https://github.com/Debanshu777/Siren.git") }
        }
    }
}
