# Releasing Siren

Version `0.1.0` is an unpublished release candidate. The release pipeline and installation examples are prepared for that coordinate; they do not establish that it resolves from Maven Central. The manual signing-only check is pushed to GitHub and can be dispatched independently. Source and nonpublishing CI preparation are authorized; no release, tag, Central upload/publication or Medium update is authorized.

## Signing-only verification

The manual [Check signing secrets](../.github/workflows/signing-check.yml) workflow uses an isolated [Gradle probe](../validation/signing-check/README.md), the saved signing secrets and your full public fingerprint. It verifies a signature against the published public key without publishing Siren. Maven credential presence is checked; authentication is not tested. Select `passphrase` for a protected key or explicitly `unprotected` for a key without one. Unprotected mode ignores `SIGNING_PASSWORD` and supplies an empty password to Gradle. Run this check after adding or rotating signing secrets. A green result does not waive the native redistribution or SDK artifact gates.

## Release configuration

The library uses Vanniktech Maven Publish and Dokka, following ShaderX's publishing approach. `sirenVersion` in root `gradle.properties` controls every target publication. The group is `io.github.debanshu777`; consumers use the root artifact `siren`, which selects Android, JVM or an iOS target through Gradle metadata.

The SDK publishes source and API documentation archives alongside its artifacts. Signing is mandatory for Central commands; local staging works without signing credentials. Public API baselines live under `siren/api/` for Android, JVM and native targets.

## Local verification

From the repository root:

```bash
./gradlew checkSpeechSetup :siren:checkKotlinAbi :siren:jvmTest :sample:composeApp:jvmTest
./gradlew validatePublishedSirenConsumer
./gradlew verifyNativeRedistribution
```

The consumer task stages publications, copies the fixture, repository and sample models outside the checkout, then tests the actual artifacts. It checks native JVM inference, Android compilation/APK packaging and, on macOS, both dynamic iOS framework links. The fixture contains no SDK project dependency, substitution or native checkout paths.

The redistribution gate checks the exact runtime pins and notice inventory in `scripts/dependencies.json`. Its report is `build/reports/native-redistribution.json`. The current technical inventory passes with 33 pinned texts and four compiler-runtime reviews. Original Windows build logs establish exact release toolsets; original ONNX Linux/Windows libraries byte-match their source-pinned archives. Vendored notice inputs and compiler evidence are hash-checked; see [third-party status](../THIRD_PARTY_NOTICES.md). Updating an overall status flag cannot waive incomplete component or payload reviews. Ordinary local builds remain available.

Use `:siren:updateKotlinAbi` only after reviewing an intentional API change, then commit the reference diff. Do not regenerate baselines automatically to make CI pass.

## Future GitHub release

When the local changes are reviewed, push them to `main`. Configure these Actions secrets in the Siren repository using the existing Central namespace and signing identity:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `GPG_KEY_CONTENTS`
- `SIGNING_KEY_ID`
- `SIGNING_PASSWORD`

The prepared release workflow uses the same explicit key-protection input as the signing check. `SIGNING_PASSWORD` is required only for protected keys and is ignored in unprotected mode.

Keep values out of source, logs and issue/chat messages. ShaderX's repository secrets cannot be read back and copied through GitHub's API.

### Where the five values come from

GitHub exposes secret names and timestamps, not their saved values. Recover the originals from your own backup or replace them in the Siren repository; existing ShaderX secrets can stay as they are.

| Secret | Source |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Generated token username from [Central Portal user tokens](https://central.sonatype.com/usertoken), not the account login |
| `MAVEN_CENTRAL_PASSWORD` | The matching generated token password; credentials are shown only when created |
| `GPG_KEY_CONTENTS` | ASCII-armored export of the signing **private** key from the original machine/key backup |
| `SIGNING_KEY_ID` | The signing key ID shown by `gpg --list-secret-keys --keyid-format SHORT` |
| `SIGNING_PASSWORD` | The passphrase chosen when protecting that private key |

On the Mac holding that key, copy its export directly to the clipboard rather than printing private material in logs:

```bash
gpg --list-secret-keys --keyid-format SHORT
gpg --armor --export-secret-keys YOUR_KEY_ID | pbcopy
```

Paste the full private-key block only into `GPG_KEY_CONTENTS` in GitHub's secret form. Do not commit it or paste it into chat. An expired/lost Central token needs a replacement; a lost private key or passphrase requires a new signing setup and distribution of its public key. See [Sonatype's token instructions](https://central.sonatype.org/publish/generate-portal-token/), [GPG guidance](https://central.sonatype.org/publish/requirements/gpg/) and [publisher signing configuration](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).

For verification, run **Release Siren** manually from `main` with `confirmVersion=0.1.0`, the matching key protection mode and `publishRelease=false` (the default). This runs the gates and signs candidates locally. Only a later explicitly authorized dispatch with `publishRelease=true` uploads to Central and creates a tag/release. Pushes and pull requests run checks; they do not publish.

The workflow defaults to verification-only (`publishRelease=false`). It validates the version, clean checkout, dispatched commit, curated changelog, absent release tag and absent Central coordinate. It checks credentials and native redistribution, tests source/API/sample builds, exercises native models and validates relocated artifact consumers. It signs all five candidate publications locally. Only when `publishRelease=true` does it reserve the tag atomically, publish signed artifacts, verify public delivery and resolve them from a separate Central-only consumer before creating the GitHub release. Central propagation is polled with a bounded timeout. Reports are uploaded even when a step fails.

Both the workflow and direct Central Gradle commands depend on the redistribution gate. There is no release bypass property. The first release remains pending until the real public artifacts and consumer have passed; update the README and Medium dependency status at that point.

## Failed publication or propagation

Inspect the uploaded reports and Central Portal deployment before retrying. A reserved tag or an already published coordinate deliberately stops a fresh release attempt. Central versions are immutable: do not overwrite or relabel published bytes. If upload succeeded but public delivery or release creation failed, verify the existing version and finish that release rather than uploading a replacement. Use a new version when the artifact itself must change.

## Recent Changes

Added read-only Central authentication, completed compiler notice/evidence coverage and default nonpublishing preflight with local candidate signing. Publication remains deferred.

Android minimum SDK is 27 throughout the library, samples and isolated consumer. The pinned ONNX Runtime Android build uses `--android_api 27`, corroborated by the bundled ELF Android API notes; declaring 26 would understate that build floor.

Relocated staging consumers resolve build-tool and non-Siren dependencies online by default; an explicit outer `--offline` flag is propagated. Siren coordinates remain exclusively bound to the copied repository, so allowing plugin downloads cannot substitute a public SDK artifact.
