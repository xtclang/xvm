import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.io.ByteArrayOutputStream
import kotlin.io.path.Path

data class GitLabel(val project: Project, val semanticVersion: SemanticVersion) {
    companion object {
        private const val DRY_RUN = false
        private const val LOG_OUTPUT = false
    }

    private val artifactBaseVersion = semanticVersion.artifactVersion.removeSuffix("-SNAPSHOT")
    private val tagPrefix = if (semanticVersion.isSnapshot()) "snapshot" else ""
    private val localTag = "${tagPrefix}/v$artifactBaseVersion"
    private val remoteTag = "refs/tags/$tagPrefix/v$artifactBaseVersion"

    // An exit value from a git process execution + its output
    data class GitResult(val execResult: Pair<Int, String>) {
        val exitValue: Int = execResult.first
        val output: String = execResult.second

        fun lines(): List<String> = output.lines()
    }

    // Information about all relevant tagging per semanticVersion publication.
    data class GitTagInfo(
        val tagsNeeded: Pair<String, String>,
        val localBranchName: String,
        val remoteBranchName: String,
        val localLastCommit: String,
        val remoteLastCommit: String,
        val localTags: List<String>,
        val remoteTags: List<String>,
    ) {
        fun localTagExists(): Boolean = localTags.contains(tagsNeeded.first)

        fun remoteTagExists(): Boolean = remoteTags.contains(tagsNeeded.second)

        fun tagExists(): Boolean = localTagExists() || remoteTagExists()

        override fun toString(): String {
            return buildString {
                appendLine("tagsNeeded       : $tagsNeeded")
                appendLine("localBranchName  : $localBranchName")
                appendLine("remoteBranchName : $remoteBranchName")
                appendLine("localLastCommit  : $localLastCommit")
                appendLine("remoteLastCommit : $remoteLastCommit")
                appendLine("localTags        : $localTags")
                append("remoteTags       : $remoteTags")
            }
        }
    }

    private fun fetchTags() {
        git("fetch", "--force", "--tags")
    }

    /**
     * Ask GitHub about all published packages for the xtclang organization.
     *
     * @return map of package names to maps of versions and when they were updated.
     */
    fun resolvePackages(logLevel: LogLevel = LogLevel.LIFECYCLE): Map<String, Map<String, List<String>>> = project.run {
        val (_, output) = gh("api", "https://api.github.com/orgs/xtclang/packages?package_type=maven")
        val packages = Json.parseToJsonElement(output)
        require(packages is JsonArray)

        val map = buildMap<String, Map<String, List<String>>> {
            packages.map { obj -> (obj as JsonObject) }.forEach { node ->
                val packageName = node["name"]!!.jsonPrimitive.content.removeSurrounding("\"")
                val packageVersionCount = node["version_count"]!!.jsonPrimitive.int
                require(packageVersionCount > 0) { "Package '$packageName' has no versions." }
                val (_, outVer) = gh("api","https://api.github.com/orgs/xtclang/packages/maven/$packageName/versions")
                val versionArray = Json.parseToJsonElement(outVer)
                require(versionArray is JsonArray)
                val timeMap = mutableMapOf<String, MutableList<String>>()
                versionArray.map { it as? JsonObject ?: throw buildException("Expected JsonObject for version") }
                    .forEach { version ->
                        val versionName =
                            version["name"]?.jsonPrimitive?.content ?: throw buildException("Version name not found")
                        val versionUpdate = version["updated_at"]?.jsonPrimitive?.content
                            ?: throw buildException("Version updated time not found")
                        val timestamps = timeMap.getOrPut(versionName) { mutableListOf() }
                        timestamps.add(versionUpdate)
                    }
                put(packageName, timeMap)
            }
        }

        logger.lifecycle("$prefix Listing all packages and their creation timestamps:")
        map.forEach { (packageName, versionMap) ->
            logger.lifecycle("$prefix   Package: '$packageName'")
            versionMap.forEach { (version, timestamps) ->
                logger.lifecycle("$prefix      Version: '$version'")
                timestamps.forEach { timestamp -> logger.lifecycle("$prefix             Created at: $timestamp") }
            }
        }
        return map
    }

    fun resolveTags(logLevel: LogLevel = LogLevel.LIFECYCLE): GitTagInfo = project.run {
        val localBranchName = git("branch", "--show-current").output
        return GitTagInfo(
            localTag to remoteTag,
            localBranchName,
            "remotes/origin/$localBranchName",
            git("rev-parse", "HEAD").output, // localLastCommit
            git("ls-remote", "origin", "HEAD").output.removeSuffix("HEAD").trim(), // remoteLastCommit
            git("tag", "--list").output.lines().toList(), // localTags
            git("ls-remote", "--tags", "origin").output.lines().map { it: String ->
                it.split("\\s+".toRegex(), 2).last()
            }.toList()
        ).also {
            it.toString().lines().forEach { line -> logger.log(logLevel, "$prefix [git tag info] $line") }
        }
    }

    /**
     * Calculate the tag based on the semantic version, and depending on whwther it's a snapshot
     * or not, replace or add the tag. Fail if it's not a snapshot and we are trying to use an
     * existing version that is already tagged in the code base (and therefore, should also be
     * published before).
     *
     * The version can be overridden, for testing and for convenience when scripting GitHub
     * workflows or for similar tasks.
     *
     * @param semanticVersion The version to tag, derived from the Project by default.
     */
    fun update(): Unit = project.run {
        fetchTags()

        val tags = resolveTags()

        // The tag we want
        val localTagExists = tags.localTagExists()
        val remoteTagExists = tags.remoteTagExists()
        val isSnapshot = semanticVersion.isSnapshot()
        val isRelease = !isSnapshot

        if (localTagExists != remoteTagExists) {
            logger.warn("$prefix Local tag $localTag is not in sync with remote tag $remoteTag.")
        }

        if (isRelease && tags.tagExists()) {
            throw buildException("Cannot publish a release/non-snapshot build with an existing tag ($localTag, $remoteTag).")
        }

        if (isSnapshot) {
            if (localTagExists) {
                logger.lifecycle("$prefix Deleting tag (locale=$remoteTag)")
                git("tag", "-d", localTag) // delete from local
            }
            if (remoteTagExists) {
                logger.lifecycle("$prefix Deleting tag (remote=$remoteTag)")
                git("push", "origin", ":$remoteTag") // delete from remote
            }
        }

        // Tag is guaranteed to not exist if we reach this code.
        logger.lifecycle("$prefix Creating tag $localTag at commit ${tags.localLastCommit}")
        git("tag", localTag, tags.localLastCommit)
        git("push", "origin", "--tags")
    }

    private fun gh(vararg arguments: String): Pair<Int, String> = project.run {
        logger.lifecycle("$prefix Executing: gh ${arguments.joinToString(" ")}")
        val whichOut = ByteArrayOutputStream()
        val execResult = exec {
            standardOutput = whichOut
            executable = "which"
            args("gh")
            isIgnoreExitValue = true
        }

        if (execResult.exitValue != 0) {
            throw buildException("'gh' is not installed. Please install it from https://cli.github.com/, or with a package manager, e.g. like 'brew install gh'")
        }

        val ghRealPath = Path(whichOut.toString().trim()).toRealPath().toString()
        logger.lifecycle("$prefix Executing gh from real path: $ghRealPath")
        val ghOut = ByteArrayOutputStream()
        exec {
            standardOutput = ghOut
            executable = ghRealPath
            args(*arguments)
        }
        return 0 to ghOut.toString()
    }

    private fun git(vararg args: String): GitResult = project.run {
        // TODO move this to project independent spawn, but right now github actions hates that.
        val cmd = listOf("git", *args)
        return logGitOutput(
            cmd.joinToString(" "),
            GitResult(executeCommand(cmd, throwOnError = true, dryRun = DRY_RUN))
        )
    }

    private fun logGitOutput(header: String, result: GitResult): GitResult = project.run {
        val exitValue = result.exitValue
        logger.lifecycle("$prefix $header (exitValue: $exitValue)")
        if (exitValue != 0) {
            logger.warn("$prefix Git call was non-throwing, but returned non zero value: $exitValue")
        }
        if (!DRY_RUN && LOG_OUTPUT) {
            result.lines().forEach { line -> logger.lifecycle("$prefix     out: $line") }
        }
        return result
    }
}
