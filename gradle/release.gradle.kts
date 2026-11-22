import java.io.File

tasks.register<ValidateSirenRelease>("validateSirenRelease") {
    group = "publishing"
    description = "Validate main-only stable version, unpublished coordinate/tag and curated release notes"
    propertiesFile.set(layout.projectDirectory.file("gradle.properties"))
    changelogFile.set(layout.projectDirectory.file("CHANGELOG.md"))
    confirmVersion.set(providers.gradleProperty("confirmVersion").orElse(providers.environmentVariable("CONFIRM_VERSION")).orElse(""))
    branchRef.set(providers.environmentVariable("GITHUB_REF").orElse(providers.gradleProperty("releaseRef")).orElse(""))
    repository.set(providers.environmentVariable("GITHUB_REPOSITORY").orElse("Debanshu777/Siren"))
    expectedCommit.set(providers.environmentVariable("GITHUB_SHA").orElse(""))
    githubToken.set(providers.environmentVariable("GH_TOKEN").orElse(providers.environmentVariable("GITHUB_TOKEN")).orElse(""))
    githubOutputPath.set(providers.environmentVariable("GITHUB_OUTPUT").orElse(""))
    notesFile.set(layout.buildDirectory.file("reports/release/siren-release-notes.md"))
    metadataFile.set(layout.buildDirectory.file("reports/release/version.json"))
}

tasks.register<VerifySirenCentralPublication>("verifySirenCentralPublication") {
    group = "publishing"
    description = "Verify public delivery of all Siren KMP artifacts, sources, documentation and signatures"
    releaseVersion.set(providers.gradleProperty("sirenVersion"))
    attempts.set(90)
    pauseSeconds.set(20)
    reportFile.set(layout.buildDirectory.file("reports/release/central-delivery.json"))
}

tasks.register<ValidatePublishedSirenConsumer>("validateCentralSirenConsumer") {
    group = "verification"
    description = "Verify an independent relocated consumer using only publicly delivered Maven Central coordinates"
    dependsOn("prepareSpeechModels", "verifySirenCentralPublication")
    fixtureDirectory.set(layout.projectDirectory.dir("validation/published-consumer"))
    modelDirectory.set(layout.projectDirectory.dir("models"))
    releaseVersion.set(providers.gradleProperty("sirenVersion"))
    appleTargets.set(System.getProperty("os.name").contains("Mac", ignoreCase = true))
    useStagingRepository.set(false)
    val temporary = providers.environmentVariable("RUNNER_TEMP").orElse(System.getProperty("java.io.tmpdir"))
    val run = providers.environmentVariable("GITHUB_RUN_ID").orElse(rootDir.absolutePath.hashCode().toUInt().toString())
    val attempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").orElse("local")
    workspaceDirectory.set(layout.dir(temporary.zip(run.zip(attempt) { id, count -> "siren-central-consumer-$id-$count" }) { parent, name -> File(parent, name) }))
    reportFile.set(layout.buildDirectory.file("reports/validation/central-consumer.json"))
}

tasks.register<ValidatePublishedSirenConsumer>("validatePublishedSirenConsumer") {
    group = "verification"
    description = "Stage all supported publications and verify an independent relocated Maven consumer"
    val apple = System.getProperty("os.name").contains("Mac", ignoreCase = true)
    dependsOn("prepareSpeechModels")
    if (apple) dependsOn(":siren:publishAllPublicationsToStagingRepository")
    else dependsOn(":siren:publishKotlinMultiplatformPublicationToStagingRepository",
        ":siren:publishJvmPublicationToStagingRepository", ":siren:publishAndroidPublicationToStagingRepository")
    fixtureDirectory.set(layout.projectDirectory.dir("validation/published-consumer"))
    stagingRepository.set(layout.buildDirectory.dir("staging-repository"))
    modelDirectory.set(layout.projectDirectory.dir("models"))
    releaseVersion.set(providers.gradleProperty("sirenVersion"))
    appleTargets.set(apple)
    val temporary = providers.environmentVariable("RUNNER_TEMP").orElse(System.getProperty("java.io.tmpdir"))
    val run = providers.environmentVariable("GITHUB_RUN_ID").orElse(rootDir.absolutePath.hashCode().toUInt().toString())
    val attempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").orElse("local")
    workspaceDirectory.set(layout.dir(temporary.zip(run.zip(attempt) { id, count -> "siren-consumer-$id-$count" }) { parent, name -> File(parent, name) }))
    reportFile.set(layout.buildDirectory.file("reports/validation/published-consumer.json"))
}
