import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP
import java.io.ByteArrayOutputStream

plugins {
    id("org.xvm.build.version")
    id("maven-publish")
}

internal val githubClient: XtcGitHubClient by lazy { XtcGitHubClient(project) }

/**
 * Configure repositories to publish artifacts to.
 */
publishing {
    repositories {
        /*
         * Configure publication to the local Maven repository. We always look in the
         * mavenLocal repo first.
         */
        logger.info("$prefix Configuring publications for repository mavenLocal().")
        mavenLocal()

        /*
         * Configure publication to the GitHub Maven repository. We look in the GitHub repo if there
         * was no hit in the mavenLocal repo.
         */
        logger.info("$prefix Configuring publications for GitHub Maven repository.")
        if (githubClient.verifyConfig()) {
            maven {
                logger.info("$prefix Publication repository (GitHub) at: $githubClient.")
                name = XtcGitHubClient.GITHUB_TASK_GROUP
                url = uri(githubClient.gitHubUrl)
                credentials {
                    logger.info("$prefix XTC GitHub username: ${githubClient.gitHubUser}, organization: ${githubClient.gitHubOrganization}")
                    username = githubClient.gitHubUser
                    password = githubClient.gitHubToken
                }
            }
        } else {
            logger.warn("$prefix GitHub credentials are not set, publications to GitHub will be disabled.")
        }
    }
}

/**
 * Helper class to access GitHub packages for the "xtclang" org.
 */
class XtcGitHubClient @Inject constructor(project: Project) {
    val gitHubUser: String = xdkPropertyOrgXvm("github.user", "")
    val gitHubOrganization: String = xdkPropertyOrgXvm("github.organization", "xtclang")
    val gitHubToken: String = xdkPropertyOrgXvm("github.token", "")
    val gitHubUrl = xdkPropertyOrgXvm("github.repository.url", "")

    private val logger: Logger = project.logger

    fun verifyConfig(): Boolean {
        val hasGitHubUser = gitHubUser.isNotEmpty()
        val hasGitHubToken = gitHubToken.isNotEmpty()
        val hasGitHubUrl = gitHubUrl.isNotEmpty()
        if (hasGitHubUser || hasGitHubToken || hasGitHubUrl) {
            logger.warn(
                """
                    $prefix GitHub credentials are not completely set; publication to GitHub will be disabled.
                    $prefix   'org.xvm.github.user'            [configured: $hasGitHubUser]                 
                    $prefix   'org.xvm.github.token'           [configured: $hasGitHubToken]
                    $prefix   'org.xvm.github.repository.url'  [configured: $hasGitHubUrl]
                    """.trimIndent()
            )
            return false
        }

        if (gitHubUrl != gitHubUser.lowercase()) {
            throw buildException("$prefix The repository URL '$gitHubUrl' needs to contain all-lowercase owner and repository names.")
        }

        return true
    }

    fun listPackages(): String? {
        checkToken("org.xvm.github.token", gitHubToken)
        return execute(
            "-L",
            "-H", ACCEPT,
            "-H", API_VERSION,
            "-H", authorization(gitHubToken),
            "https://api.github.com/orgs/$gitHubOrganization/packages?package_type=maven"
        )
    }

    fun deletePackage(packageName: String): String? {
        checkToken("org.xvm.github.token", gitHubToken)
        return execute(
            "-L",
            "-X", "DELETE",
            "-H", ACCEPT,
            "-H", API_VERSION,
            "-H", authorization(gitHubToken),
            "https://api.github.com/orgs/$gitHubOrganization/packages/maven/$packageName"
        )
    }

    private fun execute(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val restCall = redactedArgs(*args).joinToString(" ")

        logger.lifecycle("$prefix Sending REST API query: $restCall")

        val result = project.exec {
            executable("curl")
            args(*args)
            standardOutput = stdout
            errorOutput = stderr
            isIgnoreExitValue = true
        }

        if (result.exitValue != 0) {
            logger.error("$prefix Return value ${result.exitValue}, REST API call failed: $restCall")
        }

        return stdout.toString().trim().ifEmpty { null }
    }

    fun parseJsonResponse(json: String?) {
        val response = buildString {
            append("GitHub JSON response:")
            if (json.isNullOrEmpty()) {
                append(" ($json)");
                return@buildString
            }
            json.lines().forEach {
                appendLine("    $it")
            }
        }

        // TODO: Just log the raw JSON output for now. We can turn it into the appropriate POJOs/POKOs later.
        response.lines().forEach {
            logger.lifecycle("$prefix $it")
        }
    }

    /**
     * Make sure that we do not log any secrets. A token captures by the output and showing up in a GitHub action,
     * or a Gradle build scan (which can be deleted only by sending an e-mail to Gradle Inc.) is a security risk,
     * and would have to be revoked immediately.
     */
    private fun redactedArgs(vararg args: String): List<String> {
        return buildList { args.toList().map { it.replace(gitHubToken, "[REDACTED]") }.forEach(::add) }
    }

    private fun checkToken(@Suppress("SameParameterValue") propertyName: String, token: String) {
        if (token.isEmpty()) {
            throw buildException("'$propertyName' property has no value. Please set it in a *.properties file in the project tree, or in [\$GRADLE_USER_HOME/gradle.properties] for secrets.")
        }
    }

    companion object Rest {
        const val GITHUB_TASK_GROUP = "github"

        const val ACCEPT = "Accept: application/vnd.github+json"
        const val AUTH = "Authorization: Bearer "
        const val API_VERSION = "X-GitHub-Api-Version: 2022-11-28"

        fun authorization(token: String): String = "Authorization: Bearer $token"
    }
}

tasks.filter { it.group == PUBLISH_TASK_GROUP }.forEach {
    val taskGroup = it.group
    val taskName = it.name

    logger.lifecycle("$prefix Publication task '$taskGroup.$taskName' detected.")

    fun sanityCheckPublication(taskName: String): Boolean {
        val isLocalRepo = taskName.contains("Local")
        val version = project.version.toString()
        logger.lifecycle("$prefix Sanity checking publication config for ${project.group}:${project.name}:$version")
        if (gradle.startParameter.isParallelProjectExecutionEnabled) {
            logger.warn("$prefix WARNING: Parallel project execution is enabled; publication may fail.")
        }
        if (version == Project.DEFAULT_VERSION) {
            logger.error("$prefix Project is attempting to register a publication without project version being resolved.")
            return false
        }
        if (version.contains("-SNAPSHOT")) {
            // Verify that snapshots aren't published to non-local repositories.
            if (!isLocalRepo) {
                logger.error("$prefix Task '$taskName' can't publish SNAPSHOT version to non-local repository.")
                return false
            }
            logger.warn("$prefix '$taskName' will publish ${project.version} to mavenLocal(), even though it is a SNAPSHOT version.")
        }
        logger.lifecycle("$prefix Sanity check passed for publication task '$taskName'")
        return true
    }

    tasks.named(taskName) {
        onlyIf {
            sanityCheckPublication(taskName)
        }
        doLast {
            logger.lifecycle("$prefix Finished publication task '$taskName'.")
        }
    }
}

/**
 * Task:
 *
 * List all GitHub packages of the XTC org.
 * TODO: Add structured JSON parsing for response.
 */
val listGitHubPackages by tasks.registering {
    group = XtcGitHubClient.GITHUB_TASK_GROUP
    description = "List all packages published by the $"
    doLast {
        githubClient.parseJsonResponse(githubClient.listPackages())
    }
}

/**
 * Task:
 *
 * Delete all packages for the org, for the current version.
 * If this is not a snapshot version, for safety reasons it's a no-op.
 *
 * ABSOLUTELY use this with caution.
 */
val deleteCurrentGitHubPackages by tasks.registering {
    group = XtcGitHubClient.GITHUB_TASK_GROUP
    description = "DANGEROUS: Delete all packages published by the organization."
    doLast {
        listOfNotNull(
            "org.xvm.xdk",
            "org.xvm.xtc-plugin.org.xvm.xtc-plugin.gradle.plugin",
            "org.xvm.xtc-plugin",
            "org.xvm.plugin.xtc-plugin"
        ).forEach { artifact ->
            githubClient.parseJsonResponse(githubClient.deletePackage(artifact))
        }
    }
}

// TODO add a task that bumps the XDK version and publishes.
