# Releasing Siren

Version `0.1.0` is prepared locally and is not published. The release pipeline and installation examples are prepared for that coordinate; they do not establish that it resolves from Maven Central. No release, tag, workflow dispatch, GitHub push or Medium update is part of the current local preparation.

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

The redistribution gate checks the exact runtime pins and notice inventory in `scripts/dependencies.json`. Its report is `build/reports/native-redistribution.json`. It currently rejects public publication because the complete Sherpa binary transitive/compiler-runtime inventory and source obligations remain unresolved. ONNX Runtime source correspondence and exact-commit license/third-party notices are now verified, including byte-matched Linux libraries; see [third-party status](../THIRD_PARTY_NOTICES.md). Updating an overall status flag cannot waive incomplete component or payload reviews. Ordinary local builds remain available.

Use `:siren:updateKotlinAbi` only after reviewing an intentional API change, then commit the reference diff. Do not regenerate baselines automatically to make CI pass.

## Future GitHub release

When the local changes are reviewed, push them to `main`. Configure these Actions secrets in the Siren repository using the existing Central namespace and signing identity:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `GPG_KEY_CONTENTS`
- `SIGNING_KEY_ID`
- `SIGNING_PASSWORD`

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

After the native redistribution review is complete and CI passes, run **Release Siren** manually from `main`, entering the exact configured version as `confirmVersion`. Pushes and pull requests run checks; they do not publish.

The release workflow validates the version, clean checkout, dispatched commit, curated changelog, absent release tag and absent Central coordinate. It checks credentials and native redistribution, tests source/API/sample builds, exercises native models and validates relocated artifact consumers. It then reserves the tag atomically, publishes signed artifacts, verifies public delivery and resolves them from a separate Central-only consumer before creating the GitHub release. Central propagation is polled with a bounded timeout. Reports are uploaded even when a step fails.

Both the workflow and direct Central Gradle commands depend on the redistribution gate. There is no release bypass property. The first release remains pending until the real public artifacts and consumer have passed; update the README and Medium dependency status at that point.

## Failed publication or propagation

Inspect the uploaded reports and Central Portal deployment before retrying. A reserved tag or an already published coordinate deliberately stops a fresh release attempt. Central versions are immutable: do not overwrite or relabel published bytes. If upload succeeded but public delivery or release creation failed, verify the existing version and finish that release rather than uploading a replacement. Use a new version when the artifact itself must change.

## Recent Changes

Documented the origins of all five publishing secrets, including generated Central token credentials and the original GPG private key/passphrase. Publication remains deferred.
