# Signing-secret check

This independent Gradle build signs a harmless text probe using the same in-memory
PGP signing method used by Siren's prepared publication configuration. It has no
publishing plugin, publication repository, SDK/native build or model dependency.

The manual [Check signing secrets](../../.github/workflows/signing-check.yml)
workflow runs only from `main`. Supply your signing key's full public fingerprint.
It checks all five secret names, requires `SIGNING_KEY_ID` to match the fingerprint's
last eight characters, signs using `GPG_KEY_CONTENTS` and `SIGNING_PASSWORD`, then
uses GnuPG to verify the signature with the public key downloaded from Ubuntu's
keyserver. The private key stays in Gradle process memory, and no passphrase or key
contents are printed or uploaded. Build/configuration caches are disabled.

The fixture requires these environment variables:

- `EXPECTED_SIGNING_FINGERPRINT`: full 40-character hexadecimal public fingerprint.
- `SIGNING_KEY_ID`: matching eight-character key ID.
- `GPG_KEY_CONTENTS`: full ASCII-armored private-key export.
- `SIGNING_PASSWORD`: its passphrase.

From the repository root, after supplying those values privately:

```bash
./gradlew -p validation/signing-check signProbe \
  --no-daemon --no-configuration-cache --no-build-cache --console=plain
```

CI also checks that the two Maven Central credential secrets are nonempty. It does
not authenticate them, contact a deployment endpoint or establish SDK release
readiness. The workflow has read-only repository permissions and runs no tag,
release or publication commands. Logs and the job summary provide the result;
private material is never an artifact.

## Recent Changes

Added an isolated signing probe and manual CI verification against a published
public key, without running a Siren release.
