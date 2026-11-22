import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.Base64

tasks.register("verifyCentralCredentials") {
    group = "verification"
    description = "Verify token authentication and namespace access with read-only Central API requests"
    val report = layout.buildDirectory.file("reports/central-credentials.json")
    outputs.file(report)
    outputs.upToDateWhen { false }
    doLast {
        fun required(name: String): String = providers.environmentVariable(name).orNull
            ?.takeIf { it.isNotBlank() } ?: throw GradleException("Missing credential: $name")
        val username = required("MAVEN_CENTRAL_USERNAME")
        val password = required("MAVEN_CENTRAL_PASSWORD")
        val namespace = "io.github.debanshu777"
        // Fixed HTTPS destination, GET only, no redirects and no deploy/upload endpoint.
        val endpoint = URI("https://central.sonatype.com/api/v1/publisher/deployments?namespace=$namespace&page=0&size=1")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build()
        fun request(authorization: String?): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
                .header("Accept", "application/json").GET()
            authorization?.let { builder.header("Authorization", it) }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(UTF_8))
        }
        val control = request(null)
        check(control.statusCode() == 401) { "Unauthenticated control must return HTTP 401; got ${control.statusCode()}. Credentials were not sent." }
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray(UTF_8))
        val authenticated = request("Bearer $encoded")
        val contentType = authenticated.headers().firstValue("Content-Type").orElse("")
        val validResponse = authenticated.statusCode() == 200 && contentType.contains("application/json") &&
            Regex("\"deployments\"\\s*:\\s*\\[").containsMatchIn(authenticated.body())
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                {"namespace":"$namespace","method":"GET","unauthenticatedStatus":${control.statusCode()},"authenticatedStatus":${authenticated.statusCode()},"authenticationAndNamespaceAccessVerified":$validResponse,"deploymentCreated":false,"publicationExecuted":false}
            """.trimIndent() + "\n")
        }
        check(validResponse) { "Central credential/namespace check failed (HTTP ${authenticated.statusCode()}); response body and credentials withheld" }
        logger.lifecycle("Central token authentication and namespace access verified with read-only GET requests.")
    }
}
