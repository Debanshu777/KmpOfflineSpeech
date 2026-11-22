val addonVersion = project.version.toString()

tasks.register<ValidateModelAddonRelease>("validateModelAddonRelease") {
    releaseVersion.set(addonVersion)
    confirmedVersion.set(providers.environmentVariable("CONFIRM_VERSION").orElse(""))
    branchRef.set(providers.environmentVariable("GITHUB_REF").orElse(""))
    expectedCommit.set(providers.environmentVariable("GITHUB_SHA").orElse(""))
    githubToken.set(providers.environmentVariable("GH_TOKEN").orElse(""))
    githubOutput.set(providers.environmentVariable("GITHUB_OUTPUT").orElse(""))
    repositoryRoot.set(layout.projectDirectory.dir(".."))
    changelog.set(layout.projectDirectory.file("../CHANGELOG.md"))
    report.set(layout.buildDirectory.file("reports/release/version.json"))
    releaseNotes.set(layout.buildDirectory.file("reports/release/model-release-notes.md"))
}

val verifyArtifacts = tasks.register<VerifyModelAddonArtifacts>("verifyModelAddonArtifacts") {
    dependsOn("publishAllPublicationsToStagingRepository")
    releaseVersion.set(addonVersion)
    stagingRepository.set(layout.buildDirectory.dir("staging-repository"))
    report.set(layout.buildDirectory.file("reports/release/staged-artifacts.json"))
}
tasks.matching { it.name.contains("MavenCentral") }.configureEach { dependsOn(verifyArtifacts) }

tasks.register<VerifyModelAddonPublication>("verifyModelAddonPublication") {
    releaseVersion.set(addonVersion)
    candidateJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    report.set(layout.buildDirectory.file("reports/release/central-delivery.json"))
}
