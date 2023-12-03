import XdkProperties.Companion.REDACTED
import com.fasterxml.jackson.databind.JsonNode
import io.github.rybalkinsd.kohttp.dsl.context.Method
import io.github.rybalkinsd.kohttp.dsl.context.Method.DELETE
import io.github.rybalkinsd.kohttp.dsl.context.Method.GET
import io.github.rybalkinsd.kohttp.dsl.http
import io.github.rybalkinsd.kohttp.jackson.ext.toJson
import okhttp3.Response
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Helper class to access GitHub packages for the "xtclang" org, and other build logic
 * for publishing XDK build artifacts.
 */
class GitHubPackages(buildLogic: XdkBuildLogic) {
    companion object Protocol {
        const val GITHUB_PUBLICATION_NAME = "GitHub"
        const val GITHUB_HOST = "api.github.com"
        const val GITHUB_SCHEME = "https"
        const val GITHUB_JSON_PACKAGE_NAME = "name"

        const val GITHUB_PREFIX = "org.xtclang.repo.github"
        const val GITHUB_ORG = "$GITHUB_PREFIX.org"
        const val GITHUB_USER = "$GITHUB_PREFIX.user"
        const val GITHUB_TOKEN = "$GITHUB_PREFIX.token"
        const val GITHUB_URL = "$GITHUB_PREFIX.url"
        const val GITHUB_READONLY = "$GITHUB_PREFIX.readonly"

        private const val GITHUB_URL_DEFAULT_VALUE = "https://maven.pkg.github.com/xtclang"
        private const val GITHUB_ORG_DEFAULT_VALUE = "xtclang"
        private const val GITHUB_USER_RO_DEFAULT_VALUE = "xtclang-bot"
        private const val GITHUB_TOKEN_RO_DEFAULT_VALUE = "Z2hwX0ZjNGRWeDhNYmxPcnZDYWZrRW96Q0NrQXAzaVZ5RjBUb0NheAo="

        fun restHeaders(token: String): List<Pair<String, String>> = listOfNotNull(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Authorization" to "Bearer $token")
    }

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix

    private val org: String
    private val packagesUrl: String
    private val credentials: Pair<String, String>

    init {
        with(buildLogic) {
            org = xdkProperty(GITHUB_ORG, GITHUB_ORG_DEFAULT_VALUE)
            packagesUrl = xdkProperty(GITHUB_URL, GITHUB_URL_DEFAULT_VALUE)
            val user = xdkProperty(GITHUB_USER, GITHUB_USER_RO_DEFAULT_VALUE)
            val ro = xdkPropertyBool(GITHUB_READONLY) || user == GITHUB_USER_RO_DEFAULT_VALUE
            credentials = user to (if (ro) decodeToken(GITHUB_TOKEN_RO_DEFAULT_VALUE) else xdkProperty(GITHUB_TOKEN, ""))
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeToken(str: String): String {
        return runCatching { Base64.decode(str).toString(Charsets.UTF_8).trim() }.getOrDefault("")
    }

    val uri: String get() =
        packagesUrl

    val user: String get() =
        credentials.first

    val token: String get() =
        credentials.second

    val organization: String get() =
        this.org

    val isReadOnly: Boolean get() =
        user == GITHUB_TOKEN_RO_DEFAULT_VALUE && token == decodeToken(GITHUB_TOKEN_RO_DEFAULT_VALUE)

    fun queryXtcLangPackageNames(): List<String> {
        return buildList {
            val (_, json) = restCall(
                GET,
                "/orgs/$org/packages",
                "package_type" to "maven"
            )
            json?.forEach { node -> node[GITHUB_JSON_PACKAGE_NAME]?.asText()?.also { add(it) } }
        }.filter {
            it.contains(project.group.toString()) && it.contains(project.name)
        }
    }

    fun queryXtcLangPackageVersions(packageName: String): List<String> {
        val (_, json) = restCall(GET, "/orgs/$org/packages/maven/$packageName/versions")
        return buildList {
            json?.forEach { node -> node[GITHUB_JSON_PACKAGE_NAME]?.asText()?.also { add(it) } }
        }
    }

    fun deleteXtcLangPackages(): Int {
        val packageNames = queryXtcLangPackageNames()
        if (packageNames.isEmpty()) {
            logger.warn("$prefix No Maven packages found to delete.")
            return 0
        }
        packageNames.forEach {
            deleteXtcLangPackage(it)
        }
        return packageNames.size
    }

    private fun deleteXtcLangPackage(packageName: String) {
        logger.lifecycle("$prefix Deleting package: '$packageName'")
        restCall(DELETE, "/orgs/$org/packages/maven/$packageName")
    }

    fun verifyGitHubConfig(): Boolean {
        val (user, token) = credentials
        val hasGitHubUser = user.isNotEmpty()
        val hasGitHubToken = token.isNotEmpty()
        val hasGitHubUrl = uri.isNotEmpty()
        val hasGitHubConfig = hasGitHubUser && hasGitHubToken && hasGitHubUrl
        if (!hasGitHubConfig) {
            logger.warn(
                """
                    $prefix GitHub credentials are not completely set; publication to GitHub will be disabled.
                    $prefix   '$GITHUB_PREFIX.repository.url'  [configured: $hasGitHubUrl ($uri)]
                    $prefix   '$GITHUB_PREFIX.user'            [configured: $hasGitHubUser ($user)]                 
                    $prefix   '$GITHUB_PREFIX.token'           [configured: $hasGitHubToken ($REDACTED)]
                    """.trimIndent()
            )
            return false
        }

        logger.info("$prefix Checking GitHub repo URL: '$uri'")
        if (uri != uri.lowercase()) {
            throw project.buildException("The repository URL '$uri' needs to contain all-lowercase owner and repository names.")
        }

        logger.info("$prefix GitHub credentials appear to be well-formed. (user: '$user')")
        return true
    }

    private fun restCall(mtd: Method, httpPath: String, vararg params: Pair<String, String>): Pair<Response, JsonNode?> {
        return http(method = mtd) {
            scheme = GITHUB_SCHEME
            host = GITHUB_HOST
            path = httpPath
            header {
                val token = credentials.second
                if (token.isEmpty()) {
                    throw project.buildException("Could not resolve an access token for GitHub from the properties and/or environment.")
                }
                restHeaders(token).forEach { (k, v) -> k to v }
            }
            param {
                params.forEach { (k, v) -> k to v }
            }
        }.use {
            logger.info("$prefix REST $mtd response status code: ${it.code()}")
            if (!it.isSuccessful) {
                throw project.buildException("REST $mtd response not successful: $it (code: ${it.code()})")
            }
            it to runCatching { it.toJson() }.getOrNull()
        }
    }
}
