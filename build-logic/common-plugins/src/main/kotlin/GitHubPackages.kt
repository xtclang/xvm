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
@OptIn(ExperimentalEncodingApi::class)
class GitHubPackages(buildLogic: XdkBuildLogic) {
    @Suppress("MemberVisibilityCanBePrivate")
    companion object Rest {
        /*
         * REST API for GitHub package repository:
         *
         * Get a package for an org by name:
         *      GET: https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME
         * Delete a package for an org by name:
         *      DELETE:  https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME
         * List package versions for a package owned by an organization:
         *      GET: https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME/versions
         * Get a package version for an organization:
         *      GET: https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME/versions/VERSION_ID
         * Delete a package version for an organization:
         *      DELETE: https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME/versions/VERSION_ID
         * Get versions of a package for an organization:
         *      GET: https://api.github.com/orgs/ORG/packages/PACKAGE_TYPE/PACKAGE_NAME/versions
         *
         * The GitHub Maven package repository should, if possible, be publically available for "package:read"
         * access. It should be enough to connect the package repo to the public XVM repo. Alternatives are
         * a shared "not so secret" secret token, as described here:
         *      https://docs.github.com/en/packages/learn-github-packages/about-github-packages#authenticating-to-github-packages
         *      https://github.com/orgs/community/discussions/26634
         *      git clone https://github.com/jcansdale-test/maven-consume
         *         (can also be: docker run jcansdale/gpr encode TOKEN)
         */
        const val GITHUB_PUBLICATION_NAME = "GitHub"
        const val GITHUB_HOST = "api.github.com"
        const val SCHEME = "https"
        const val JSON_PACKAGE_NAME = "name"

        const val GITHUB = "org.xvm.github"
        const val GITHUB_ORG = "$GITHUB.organization"
        const val GITHUB_USER = "$GITHUB.user"
        const val GITHUB_TOKEN = "$GITHUB.token"
        const val GITHUB_TOKEN_RO = "$GITHUB.token.readonly"
        const val GITHUB_REPO_URL = "$GITHUB.repository.url"
        const val GITHUB_FORCE_READ_ONLY = "$GITHUB.readonly"

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
    val gitHubReadOnly: Boolean
    val gitHubUser: String
    val gitHubToken: String
    val gitHubCredentials: Pair<String, String>

    init {
        with(buildLogic) {
            fun decodeToken(str: String): String {
                return runCatching { Base64.decode(str).toString(Charsets.UTF_8).trim() }.getOrDefault("")
            }

            gitHubOrganization = getProperty(GITHUB_ORG, GITHUB_ORG_DEFAULT_VALUE)
            gitHubUrl = getProperty(GITHUB_REPO_URL, "")

            val forceRo = getPropertyBoolean(GITHUB_FORCE_READ_ONLY, false)
            if (forceRo) {
                logger.warn("$prefix *** $GITHUB_FORCE_READ_ONLY=true; forcing read-only common GitHub credentials. No publishing can take place.")
            }
            val rwToken = getProperty(GITHUB_TOKEN, System.getenv("GITHUB_TOKEN") ?: "")
            val roToken = getProperty(GITHUB_TOKEN_RO, "")
            val token: String
            val user: String
            if (forceRo || rwToken.isEmpty()) {
                // Attempt read only mode
                user = GITHUB_USER_RO_DEFAULT_VALUE
                token = decodeToken(roToken)
                gitHubReadOnly = true
                logger.lifecycle("$prefix GitHub read only mode credentials fallback. ($user, $roToken)")
            } else {
                user = getProperty(GITHUB_USER, "")
                token = rwToken
                gitHubReadOnly = false
            }

            gitHubUser = user
            gitHubToken = token
            gitHubCredentials = gitHubUser to gitHubToken
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
        val hasGitHubUser = gitHubUser.isNotEmpty()
        val hasGitHubToken = gitHubToken.isNotEmpty()
        val hasGitHubUrl = gitHubUrl.isNotEmpty()
        val hasGitHubConfig = hasGitHubUser && hasGitHubToken && hasGitHubUrl
        if (!hasGitHubConfig) {
            logger.warn(
                """
                    $prefix GitHub credentials are not completely set; publication to GitHub will be disabled.
                    $prefix   'org.xvm.github.repository.url'  [configured: $hasGitHubUrl ($gitHubUrl)]
                    $prefix   'org.xvm.github.user'            [configured: $hasGitHubUser ($gitHubUser)]                 
                    $prefix   'org.xvm.github.token'           [configured: $hasGitHubToken ([redacted])]
                    """.trimIndent()
            )
            return false
        }

        logger.info("$prefix Checking GitHub repo URL: '$gitHubUrl'")
        if (gitHubUrl != gitHubUrl.lowercase()) {
            throw project.buildException("The repository URL '$gitHubUrl' needs to contain all-lowercase owner and repository names.")
        }

        logger.lifecycle("$prefix GitHub credentials appear to be well-formed. $gitHubUser")
        return true
    }

    private fun restCall(mtd: Method, httpPath: String, vararg params: Pair<String, String>): Pair<Response, JsonNode?> {
        return http(method = mtd) {
            scheme = SCHEME
            host = GITHUB_HOST
            path = httpPath
            header {
                if (gitHubToken.isEmpty()) {
                    throw project.buildException("Could not resolve an access token for GitHub from the properties and/or environment.")
                }
                restHeaders(gitHubToken).forEach { (k, v) -> k to v }
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
