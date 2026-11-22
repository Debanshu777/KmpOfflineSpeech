package io.github.debanshu.siren.gradle

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import javax.inject.Inject

/** Optional app-build configuration. This adds no runtime API or network permission. */
abstract class SirenModelsExtension @Inject constructor(objects: ObjectFactory) {
    val recognition: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val synthesis: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val cacheDirectory: DirectoryProperty = objects.directoryProperty()
}

class SirenModelsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sirenModels", SirenModelsExtension::class.java)
        extension.cacheDirectory.convention(project.layout.projectDirectory.dir(".gradle/siren-models-cache"))
        val prepare = project.tasks.register("prepareSirenModels", PrepareSirenModels::class.java) { task ->
            task.group = "siren models"
            task.description = "Download, verify and stage the selected app-owned speech models"
            task.recognition.set(extension.recognition)
            task.synthesis.set(extension.synthesis)
            task.offline.set(project.gradle.startParameter.isOffline)
            task.downloadCache.set(extension.cacheDirectory)
            task.assetsDirectory.set(project.layout.buildDirectory.dir("generated/siren-models/assets"))
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets
                .matching { it.name == "jvmMain" }.configureEach { source ->
                    source.resources.srcDir(prepare.flatMap { it.assetsDirectory })
                }
        }
        project.pluginManager.withPlugin("java") {
            project.extensions.getByType(SourceSetContainer::class.java).named("main") { source ->
                source.resources.srcDir(prepare.flatMap { it.assetsDirectory })
            }
        }
        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            val components = project.extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
            components.finalizeDsl { dsl -> dsl.androidResources.enable = true }
            components.onVariants(components.selector().all()) { variant ->
                checkNotNull(variant.sources.assets) { "Siren model packaging requires Android assets support" }
                    .addGeneratedSourceDirectory(prepare, PrepareSirenModels::assetsDirectory)
            }
        }

        // Native KLIBs do not automatically carry resources into an iOS app. Hook the
        // standard direct-integration task and copy only the generated models folder.
        val xcodeModels = project.providers.environmentVariable("TARGET_BUILD_DIR")
            .zip(project.providers.environmentVariable("UNLOCALIZED_RESOURCES_FOLDER_PATH")) { root, relative ->
                val directory = File(root)
                require(directory.isAbsolute) { "TARGET_BUILD_DIR must be absolute" }
                val folder = ModelArtifacts.relative(relative)
                require(folder.toString().endsWith(".app")) { "Siren models must be copied into an iOS .app resource directory" }
                directory.resolve(folder.toString()).resolve("models")
            }
        val iosModels = project.tasks.register("copySirenModelsForXcode", Sync::class.java) { task ->
            task.group = "siren models"
            task.description = "Package selected models into the Xcode app, or stage them locally outside Xcode"
            task.from(prepare.flatMap { it.assetsDirectory.dir("models") })
            task.into(xcodeModels.orElse(project.layout.buildDirectory.dir("generated/siren-models/ios/models").map { it.asFile }))
        }
        project.tasks.matching { it.name == "embedAndSignAppleFrameworkForXcode" }.configureEach {
            it.dependsOn(iosModels)
        }
    }
}
