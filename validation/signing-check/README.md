# Signing-secret check

This independent Gradle build signs a harmless text probe using the same in-memory
PGP signing method used by Siren's prepared publication configuration. It has no
publishing plugin, publication repository, SDK/native build or model dependency.

The manual [Check signing secrets](../../.github/workflows/signing-check.yml)
workflow runs only from `main`. Supply your signing key's full public fingerprint.
It checks the required secret names, requires `SIGNING_KEY_ID` to match the fingerprint's
last eight characters, signs using `GPG_KEY_CONTENTS` and `SIGNING_PASSWORD`, then
uses GnuPG to verify the signature with the public key downloaded from Ubuntu's
keyserver. The private key stays in Gradle process memory, and no passphrase or key
contents are printed or uploaded. Build/configuration caches are disabled.

The fixture requires these environment variables:

- `EXPECTED_SIGNING_FINGERPRINT`: full 40-character hexadecimal public fingerprint.
- `SIGNING_KEY_ID`: matching eight-character key ID.
- `GPG_KEY_CONTENTS`: full ASCII-armored private-key export.
- `SIGNING_KEY_PROTECTION`: `passphrase` (default) or explicitly `unprotected`.
- `SIGNING_PASSWORD`: required only in passphrase mode; ignored in unprotected mode.

Choose the matching protection mode when dispatching CI. Unprotected mode passes
an empty password to Gradle and does not alter your key or any repository secret.

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

The [recorded CI run](https://github.com/Debanshu777/Siren/actions/runs/45055771902) passed with explicit unprotected mode. It verified the saved private key and key ID against the published public key; no passphrase was used. [Verification record](../signing-check-verification.json).

## Recent Changes

Added explicit protected/unprotected key modes to the isolated signing probe and
manual public-key verification, without running a Siren release.
