import com.fasterxml.jackson.databind.JsonNode
import io.github.rybalkinsd.kohttp.dsl.context.Method
import io.github.rybalkinsd.kohttp.dsl.context.Method.DELETE
import io.github.rybalkinsd.kohttp.dsl.context.Method.GET
import io.github.rybalkinsd.kohttp.dsl.http
import io.github.rybalkinsd.kohttp.jackson.ext.toJson
import okhttp3.Response

/**
 * Helper class to access GitHub packages for the "xtclang" org, and other build logic
 * for publishing XDK build artifacts.
 */
class GitHubPackages(buildLogic: XdkBuildLogic) {
    companion object Rest {
        const val GITHUB_PUBLICATION_NAME = "GitHub"
        const val GITHUB_HOST = "api.github.com"
        const val SCHEME = "https"
        const val JSON_PACKAGE_NAME = "name"

        const val GITHUB = "org.xvm.github"
        const val GITHUB_ORG = "$GITHUB.organization"
        const val GITHUB_USER = "$GITHUB.user"
        const val GITHUB_TOKEN = "$GITHUB.token"
        const val GITHUB_REPO_URL = "$GITHUB.repository.url"

        const val GITHUB_ORG_DEFAULT_VALUE = "xtclang"
        const val GITHUB_USER_RO_DEFAULT_VALUE = "xtclang-bot"

        fun restHeaders(token: String): List<Pair<String, String>> = listOfNotNull(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Authorization" to "Bearer $token")
    }

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix

    val gitHubOrganization: String
    val gitHubUrl: String
    val gitHubCredentials: Pair<String, String>
    val isReadOnly = false // TODO:

    init {
        with(buildLogic) {
            gitHubOrganization = getProperty(GITHUB_ORG, GITHUB_ORG_DEFAULT_VALUE)
            gitHubUrl = getProperty(GITHUB_REPO_URL, "")
            gitHubCredentials = getProperty(GITHUB_USER, GITHUB_USER_RO_DEFAULT_VALUE) to getProperty(GITHUB_TOKEN, "")
        }
    }

    fun queryXtcLangPackageNames(): List<String> {
        return buildList {
            val (_, json) = restCall(
                GET,
                "/orgs/$gitHubOrganization/packages",
                "package_type" to "maven"
            )
            json?.forEach { node -> node[JSON_PACKAGE_NAME]?.asText()?.also { add(it) } }
        }.filter {
            it.contains(project.group.toString()) && it.contains(project.name)
        }
    }

    fun queryXtcLangPackageVersions(packageName: String): List<String> {
        val (_, json) = restCall(GET, "/orgs/$gitHubOrganization/packages/maven/$packageName/versions")
        return buildList {
            json?.forEach { node -> node[JSON_PACKAGE_NAME]?.asText()?.also { add(it) } }
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
        restCall(DELETE, "/orgs/$gitHubOrganization/packages/maven/$packageName")
    }

    fun verifyGitHubConfig(): Boolean {
        val (user, token) = gitHubCredentials
        val hasGitHubUser = user.isNotEmpty()
        val hasGitHubToken = token.isNotEmpty()
        val hasGitHubUrl = gitHubUrl.isNotEmpty()
        val hasGitHubConfig = hasGitHubUser && hasGitHubToken && hasGitHubUrl
        if (!hasGitHubConfig) {
            logger.warn(
                """
                    $prefix GitHub credentials are not completely set; publication to GitHub will be disabled.
                    $prefix   'org.xvm.github.repository.url'  [configured: $hasGitHubUrl ($gitHubUrl)]
                    $prefix   'org.xvm.github.user'            [configured: $hasGitHubUser ($user)]                 
                    $prefix   'org.xvm.github.token'           [configured: $hasGitHubToken ([redacted])]
                    """.trimIndent()
            )
            return false
        }

        logger.info("$prefix Checking GitHub repo URL: '$gitHubUrl'")
        if (gitHubUrl != gitHubUrl.lowercase()) {
            throw project.buildException("The repository URL '$gitHubUrl' needs to contain all-lowercase owner and repository names.")
        }

        logger.info("$prefix GitHub credentials appear to be well-formed. (user: '$user')")
        return true
    }

    private fun restCall(mtd: Method, httpPath: String, vararg params: Pair<String, String>): Pair<Response, JsonNode?> {
        return http(method = mtd) {
            scheme = SCHEME
            host = GITHUB_HOST
            path = httpPath
            header {
                val token = gitHubCredentials.second
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
