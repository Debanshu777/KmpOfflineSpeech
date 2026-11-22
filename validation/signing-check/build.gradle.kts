import org.gradle.plugins.signing.Sign

plugins { signing }

fun requiredEnvironment(name: String): String = providers.environmentVariable(name).orNull
    ?.takeIf { it.isNotBlank() } ?: throw GradleException("Missing environment variable: $name")

val expectedFingerprint = requiredEnvironment("EXPECTED_SIGNING_FINGERPRINT").uppercase()
require(expectedFingerprint.matches(Regex("[0-9A-F]{40}"))) { "Expected a full 40-character fingerprint" }
val keyId = requiredEnvironment("SIGNING_KEY_ID").trim().uppercase()
require(keyId == expectedFingerprint.takeLast(8)) { "SIGNING_KEY_ID must match the expected fingerprint's last eight characters" }
val keyProtection = providers.environmentVariable("SIGNING_KEY_PROTECTION").getOrElse("passphrase")
val keyPassword = when (keyProtection) {
    "passphrase" -> requiredEnvironment("SIGNING_PASSWORD")
    "unprotected" -> ""
    else -> throw GradleException("SIGNING_KEY_PROTECTION must be passphrase or unprotected")
}

signing {
    isRequired = true
    useInMemoryPgpKeys(keyId, requiredEnvironment("GPG_KEY_CONTENTS"), keyPassword)
}

val probe = layout.buildDirectory.file("verification/probe.txt")
val prepareProbe = tasks.register("prepareProbe") {
    outputs.file(probe)
    doLast {
        probe.get().asFile.apply {
            parentFile.mkdirs()
            writeText("Siren signing-secret verification only; this is not a release artifact.\n")
        }
    }
}

tasks.register<Sign>("signProbe") {
    group = "verification"
    description = "Sign a harmless probe with the saved in-memory key; no publication plugins or repositories"
    dependsOn(prepareProbe)
    sign(probe.get().asFile)
    doLast {
        val signature = signatures.single().file
        val output = layout.buildDirectory.file("verification/probe.txt.asc").get().asFile
        if (signature.canonicalFile != output.canonicalFile) signature.copyTo(output, overwrite = true)
    }
}
