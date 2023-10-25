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
         */
        const val GITHUB_PUBLICATION_NAME = "GitHub"
        const val GITHUB_HOST = "api.github.com"
        const val SCHEME = "https"
        const val JSON_PACKAGE_NAME = "name"

        fun restHeaders(token: String): List<Pair<String, String>> = listOfNotNull(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "Authorization" to "Bearer $token")
    }

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix

    val gitHubUser: String = buildLogic.getProperty("org.xvm.github.user", "")
    val gitHubOrganization: String = buildLogic.getProperty("org.xvm.github.organization", "xtclang")
    val gitHubToken: String = buildLogic.getProperty("org.xvm.github.token", System.getenv("GITHUB_TOKEN") ?: "")
    val gitHubUrl: String = buildLogic.getProperty("org.xvm.github.repository.url", "")

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

        logger.lifecycle("$prefix Checking GitHub repo URL: '$gitHubUrl'")
        if (gitHubUrl != gitHubUrl.lowercase()) {
            throw project.buildException("$prefix The repository URL '$gitHubUrl' needs to contain all-lowercase owner and repository names.")
        }

        return true
    }

    private fun restCall(rmethod: Method, rpath: String, vararg params: Pair<String, String>): Pair<Response, JsonNode?> {
        return http(method = rmethod) {
            scheme = SCHEME
            host = GITHUB_HOST
            path = rpath //
            header {
                if (gitHubToken.isEmpty()) {
                    throw project.buildException("$prefix Could not resolve an access token for GitHub from the properties and/or environment.")
                }
                restHeaders(gitHubToken).forEach { (k, v) -> k to v }
            }
            param {
                params.forEach { (k, v) -> k to v }
            }
        }.use {
            logger.info("$prefix REST $rmethod response status code: ${it.code()}")
            if (!it.isSuccessful) {
                throw project.buildException("$prefix REST $rmethod response not successful: $it (code: ${it.code()})")
            }
            it to runCatching { it.toJson() }.getOrNull()
        }
    }
}
