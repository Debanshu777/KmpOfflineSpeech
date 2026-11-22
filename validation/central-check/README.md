# Read-only Central credential check

The manual [Check Central credentials](../../.github/workflows/central-check.yml)
workflow uses the two saved Maven secrets with Sonatype's documented
[deployment-list API](https://central.sonatype.com/api-doc). It makes only GET
requests to `/api/v1/publisher/deployments`, filtered to `io.github.debanshu777`.
An unauthenticated control must return 401; the authenticated request must return
200 with a JSON deployment-list response. A 404 means the namespace is absent or
the token cannot access it. Response bodies and credentials are never printed or
saved. The report contains only the namespace, HTTP statuses and result flags.

This independent Gradle build has no publishing plugin, SDK/model dependencies,
deployment creation or publication command. HTTPS destinations are fixed and
redirects disabled. Secrets are scoped to one CI step; caches are disabled.

To run with privately supplied `MAVEN_CENTRAL_USERNAME` and
`MAVEN_CENTRAL_PASSWORD` environment variables:

```bash
./gradlew -p validation/central-check verifyCentralCredentials \
  --no-daemon --no-configuration-cache --no-build-cache --console=plain
```

Success verifies authentication and namespace access at check time. It does not
upload a bundle, establish server-side artifact validation or publish Siren.

## Recent Changes

Added authenticated, read-only namespace verification with an unauthenticated
control and a nonsecret result report.
