import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Interactions with Git, for tagging and gh api access.
 *
 * We can also use this locally, e.g. tos setup tags for publication.
 *
 * Always start by pruning, so that we have exactly the remote tags,
 * killing all local ones.
 *
 * mavenCentral: https://central.sonatype.org/publish/requirements/#sign-files-with-gpgpgp
 */
data class GitHubProtocol(private val project: Project) {
    companion object {
        fun tagCreated(tag: Pair<String, String>): Boolean {
            return tag.first.isNotEmpty()
        }

        private fun gh(vararg args: String, throwOnError: Boolean = true, logger: Logger? = null): ProcessResult {
            return spawn("gh", *args, throwOnError = throwOnError, logger = logger)
        }

        private fun git(vararg args: String, throwOnError: Boolean = true, logger: Logger? = null): ProcessResult {
            return spawn("git", *args, throwOnError = throwOnError, logger = logger)
        }
    }

    private val semanticVersion = project.property("semanticVersion") as SemanticVersion
    private val artifactBaseVersion = semanticVersion.artifactVersion.removeSuffix("-SNAPSHOT")
    private val tagPrefix = if (semanticVersion.isSnapshot()) "snapshot/" else "release/"
    private val localTag = "${tagPrefix}v$artifactBaseVersion"
    private val remoteTag = "refs/tags/${tagPrefix}v$artifactBaseVersion"

    data class GitTagInfo(
        val tagsNeeded: Pair<String, String>,
        val localBranchName: String,
        val remoteBranchName: String,
        val localLastCommit: String,
        val remoteLastCommit: String,
        val localTagMap: Map<String, String>,
        val remoteTagMap: Map<String, String>
    ) {
        fun localTagExists(): Boolean = localTagMap[tagsNeeded.first] != null

        fun remoteTagExists(): Boolean = remoteTagMap[tagsNeeded.second] != null

        fun verifySync(): Boolean {
            return localTagExists() == remoteTagExists()
        }

        override fun toString(): String {
            return buildString {
                appendLine("tagsNeeded       : $tagsNeeded")
                appendLine("localBranchName  : $localBranchName")
                appendLine("remoteBranchName : $remoteBranchName")
                appendLine("localLastCommit  : $localLastCommit")
                appendLine("remoteLastCommit : $remoteLastCommit")
                // To find the branches a specific commit is in, we can use git branch --contains <commit hash> for debugging.
                appendLine("localTags:")
                localTagMap.forEach { (tag, commit) -> appendLine("    $tag -> $commit") }
                appendLine("remoteTags:")
                remoteTagMap.forEach { (tag, commit) -> appendLine("    $tag -> $commit") }
            }.trim()
        }
    }

    private fun resolveTagMap(isLocal: Boolean, vararg gitArgs: String): Map<String, String> {
        val map = buildMap {
            git(*gitArgs, logger = project.logger).output.lines().forEach { line ->
                val (commit, tag) = line.split("\\s+".toRegex(), 2)
                val strippedTag = if (isLocal) tag.removePrefix("refs/tags/") else tag
                put(strippedTag, commit)
            }
        }
        return map
    }

    /**
     * Make sure any local tags out of sync with remote are clobbered and/or updated.
     */
    private fun fetch(): GitTagInfo = project.run {
        git("fetch", "--tags", "--prune-tags", "--force", "--verbose", logger = logger)
        return resolveTags()
    }

    fun resolveTags(): GitTagInfo = project.run {
        val localBranchName = git("branch", "--show-current", logger = logger).output
        val localTagMap = resolveTagMap(true, "show-ref", "--tags")
        val remoteTagMap = resolveTagMap(false, "ls-remote", "--tags", "origin")
        return GitTagInfo(
            localTag to remoteTag,
            localBranchName,
            "remotes/origin/$localBranchName",
            git("rev-parse", "HEAD", logger = logger).output,
            git("ls-remote", "origin", "HEAD", logger = logger).output.removeSuffix("HEAD").trim(),
            localTagMap,
            remoteTagMap
        )
    }

    /**
     * Calculate the tag based on the semantic version, and depending on whether it's a snapshot
     * or not, replace or add the tag. Fail if it's not a snapshot, and we are trying to use an
     * existing version that is already tagged in the code base (and therefore, should also be
     * published before).
     *
     * First find
     */
    fun ensureTags(snapshotOnly: Boolean = false): Pair<String, String> = project.run {
        // Fetch and prune remote tags, making sure that we have all remote tags locally, and no local tags that aren't on the remote.
        val tags = fetch()
        assert(tags.verifySync())
        val isSnapshot = semanticVersion.isSnapshot()
        val isRelease = !isSnapshot
        val unchanged = "" to ""

        if (snapshotOnly && isRelease) {
            logger.warn("$prefix ensureTags was called with snapshotOnly set. Skipping publication of non-snapshot: $semanticVersion")
            return unchanged
        }
        if (isRelease && tags.remoteTagExists()) {
            logger.warn("$prefix The VERSION file specifies a release version, but the tag already exists in the repository. No publication will be made.")
            return unchanged
        }

        if (tags.localTagExists() || tags.remoteTagExists()) {
            logger.lifecycle("$prefix Tag $localTag already exists.")
        }

        if (isSnapshot) {
            git("tag", "-d", localTag, throwOnError = false, logger = logger)
            git("push", "--delete", "origin", localTag, throwOnError = false, logger = logger)
        }
        git("tag", localTag, logger = logger)
        git("push", "origin", localTag, logger = logger)

        return localTag to tags.localLastCommit
    }

    /**
     * Can be used to double-check that we aren't trying to publish something that is already
     * there, and immutable (i.e. a non-snapshot package). This makes it possible to exit
     * gracefully and warn, instead of getting hit with a "401 Unauthorized" or "collision" or
     * any other REST error from the remote side.
     */
    fun isPublished(): Boolean {
        val map = resolvePackages()
        map.keys.forEach { packageName ->
            val packageVersions = map[packageName]!!
            packageVersions.keys.forEach { (version, _) ->
                if (version == semanticVersion.artifactVersion) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Delete all versions of a package with a certain name, and or certain versions.
     * If no arguments are given, everything is deleted.
     */
    fun deletePackages(
        packageNames: List<String> = emptyList(),
        packageVersions: List<String> = emptyList()): Boolean =
        project.run {
            val all = resolvePackages()
            val selectedNames = packageNames.ifEmpty { all.keys }
            var changes = 0

            for (packageName in selectedNames) {
                if (packageVersions.isEmpty()) {
                    logger.warn("$prefix No versions given, will delete complete package: '$packageName'.")
                    gh(
                        "api",
                        "--method",
                        "DELETE",
                        "https://api.github.com/orgs/xtclang/packages/maven/$packageName",
                        throwOnError = false,
                        logger = logger
                    )
                    changes++
                    continue
                }

                val versionInfo = all[packageName] ?: continue
                for ((version, versionId) in versionInfo.keys) {
                    if (version in packageVersions) {
                        logger.lifecycle("$prefix Deleting package $packageName (versionName: $version, versionId: $versionId)")
                        if (isDryRun()) {
                            logger.warn("$prefix [dryRun] Skipping API call 'delete $packageName'.")
                        }
                        val result = gh(
                            "api", "--method", "DELETE",
                            "https://api.github.com/orgs/xtclang/packages/maven/$packageName/versions/$versionId",
                            throwOnError = false,
                            logger = logger
                        )
                        changes++
                        if (!result.isSuccessful()) {
                            logger.error("$prefix ERROR: Failed to delete package $packageName")
                            result.output.lines().forEach { line -> logger.error("$prefix   $line") }
                        }
                    }
                }
            }
            logger.lifecycle("$prefix Deleted $changes packages")
            return changes > 0
    }

    fun resolvePackages(
        names: Set<String> = emptySet(),
        org: String = "xtclang"
    ): Map<String, Map<Pair<String, Int>, List<String>>> = project.run {
        val result = gh("api", "https://api.github.com/orgs/$org/packages?package_type=maven", logger = logger)
        assert(result.isSuccessful())
        val packages = Json.parseToJsonElement(result.output)
        require(packages is JsonArray)
        val map = buildMap<String, Map<Pair<String, Int>, List<String>>> {
            packages.map { obj -> (obj as JsonObject) }.forEach { node ->
                val packageName = node["name"]!!.jsonPrimitive.content.removeSurrounding("\"")
                if (names.isNotEmpty() && packageName !in names) {
                    logger.info("$prefix Skipping package: '$packageName'")
                    return@forEach
                }

                val packageVersionCount = node["version_count"]!!.jsonPrimitive.int
                require(packageVersionCount > 0) { "Package '$packageName' has no versions." }

                val versions = gh(
                    "api", "https://api.github.com/orgs/$org/packages/maven/$packageName/versions",
                    logger = logger)
                assert(versions.isSuccessful())

                val versionArray = Json.parseToJsonElement(versions.output)
                require(versionArray is JsonArray)

                val timeMap = mutableMapOf<Pair<String, Int>, MutableList<String>>()
                versionArray.map { it as? JsonObject ?: throw buildException("Expected JsonObject for version") }
                    .forEach { version ->
                        val versionName =
                            version["name"]?.jsonPrimitive?.content ?: throw buildException("Version name not found")
                        val versionId =
                            version["id"]?.jsonPrimitive?.int ?: throw buildException("Version id not found")
                        val versionUpdatedAt = version["updated_at"]?.jsonPrimitive?.content
                            ?: throw buildException("Version updated time not found")
                        val timestamps = timeMap.getOrPut(versionName to versionId) { mutableListOf() }
                        timestamps.add(versionUpdatedAt)
                    }
                put(packageName, timeMap)
            }
        }

        return map
    }
}
