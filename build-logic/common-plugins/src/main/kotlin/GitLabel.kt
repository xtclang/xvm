import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

data class GitLabel(val project: Project, val semanticVersion: SemanticVersion) {

    companion object {
        private const val DRY_RUN = false
    }

    private val artifactBaseVersion = semanticVersion.artifactVersion.removeSuffix("-SNAPSHOT")
    private val tagPrefix = if (semanticVersion.isSnapshot()) "snapshot" else ""
    private val localTag = "${tagPrefix}/v$artifactBaseVersion"
    private val remoteTag = "refs/tags/$tagPrefix/v$artifactBaseVersion"

    data class GitResult(val execResult: Pair<Int, String>) {
        val exitValue: Int = execResult.first
        val output: String = execResult.second

        fun lines(): List<String> = output.lines()
    }

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

    private fun listPackages() {
        //val (exitValue, output) = project.executeCommand(listOf("gh", "api", "\"https://api.github.com/orgs/xtclang/packages?package_type=maven\""), true)
        gh("api", "\"https://api.github.com/orgs/xtclang/packages?package_type=maven\"")
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

    // For a snapshot, delete existing tag
// (if there is one), recreate and attach to latest commit.
// For a non-snapshot, fail if exists locally or remotely. Otherwise tag attach to latest commit.
// Push all tag changes to upstream as a separate step.
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

    private fun logGitOutput(header: String, result: GitResult): GitResult = project.run {
        val exitValue = result.exitValue
        logger.lifecycle("$prefix $header (exitValue: $exitValue)")
        if (exitValue != 0) {
            logger.warn("$prefix Git call was non-throwing, but returned non zero value: $exitValue")
        }
        if (!DRY_RUN) {
            result.lines().forEach { line -> logger.lifecycle("$prefix     out: $line") }
        }
        return result
    }

    fun pushTags() {
        TODO("pushTags")
    }

    fun deleteLocal(): Boolean {
        return false
    }

    fun deleteUpstream(): Boolean {
        return false
    }

    fun getLocalCommit(): String {
        return ""
    }

    fun getRemoteCommit(): String {
        return ""
    }

    fun existsLocal(): Boolean {
        return getLocalCommit().isNotEmpty()
    }

    fun existsRemote(): Boolean {
        return getRemoteCommit().isNotEmpty()
    }

    private fun gh(vararg args: String): Unit = project.run {
        executeCommand(listOf("gh", *args), throwOnError = true, dryRun = DRY_RUN)
    }

    private fun git(vararg args: String): GitResult = project.run {
        // TODO move this to project independent spawn, but right now github actions hates that.
        val cmd = listOf("git", *args)
        return logGitOutput(
            cmd.joinToString(" "),
            GitResult(executeCommand(cmd, throwOnError = true, dryRun = DRY_RUN))
        )
    }
}

/*
val en by tasks.registering {
    doLast {
        fun output(vararg args: String) = project.executeCommand(throwOnError = true, *args).second

        fun execute(throwOnError: Boolean = false, vararg args: String): Pair<Int, String> {
            val result = project.executeCommand(throwOnError, *args)
            if (result.first != 0) {
                logger.error("$prefix Git returned non zero value: $result")
            }
            return result
        }

        fun remoteTag(localTag: String): String {
            return "refs/tag/$localTag"
        }

        fun deleteTag(throwOnError: Boolean = false, localTag: String): Boolean {
            // only if tag exists
            val remoteTag = remoteTag(localTag)
            //execute(throwOnError = throwOnError, "git", "tag", "-d", localTag)
            // 1) Delete the tag on any remote before pushing
            execute(throwOnError = throwOnError, "git", "push", "origin", ":${remoteTag(localTag)}")
            logger.lifecycle("$prefix Deleted tags: (local: $localTag, remote: $remoteTag)")
            return true
        }

        fun existingCommits(localTag: String): Pair<String, String> {
            val (localTagValue, localTagCommit) = execute(throwOnError = false, "git", "rev-list", "-n", "1", localTag)
            val (remoteTagValue, remoteTagCommitAndName) = execute(
                throwOnError = false,
                "git",
                "ls-remote",
                "--tags",
                "origin",
                remoteTag(localTag)
            )
            val local = if (localTagValue == 0) localTagCommit else ""
            val remote =
                if (remoteTagValue == 0) remoteTagCommitAndName.removeSuffix(remoteTag(localTag)).trim() else ""
            return local to remote
        }

        val parsedVersion = File(compositeRootProjectDirectory.asFile, "VERSION").readText().trim()
        if (parsedVersion != version.toString()) {
            throw buildException("$prefix Version mismatch: parsed version '$parsedVersion' does not match project version '$version'")
        }
        val baseVersion = parsedVersion.removeSuffix("-SNAPSHOT")
        val isSnapshot = project.isSnapshot()

        val localTag = buildString {
            append(if (isSnapshot) "snapshot/v" else "v")
            append(baseVersion)
        }

        val remoteTag = remoteTag(localTag)
        execute(throwOnError = true, "git", "fetch", "--force", "--tags") // TODO P options?
        val localBranchName = output("git", "branch", "--show-current")
        val remoteBranchName = "remotes/origin/$localBranchName"
        val localLastCommit = output("git", "rev-parse", "HEAD")
        val remoteLastCommit = output("git", "ls-remote", "origin", "HEAD").removeSuffix("HEAD").trim()
        val (localTagCommit, remoteTagCommit) = existingCommits(localTag)
        val hasLocalTag = localTagCommit.isNotEmpty()
        val hasRemoteTag = remoteTagCommit.isNotEmpty()
        val hasTag = hasLocalTag || hasRemoteTag

        logger.lifecycle(
            """
             $prefix createTag for version $version (base version: $baseVersion)
             $prefix   isSnapshot: $isSnapshot
             $prefix   hasLocalTag: $hasLocalTag, hasRemoteTag: $hasRemoteTag
             $prefix   Local branch: '$localBranchName'
             $prefix       last commit: '$localLastCommit'
             $prefix       tag : '$localTag'
             $prefix       tag place at local commit: '$localTagCommit'
             $prefix   Remote branch: '$remoteBranchName'
             $prefix       last: '$remoteLastCommit'
             $prefix       tag : '$remoteTag'
             $prefix       tag placed at remote commit: '$remoteTagCommit'
         """.trimIndent()
        )

        if (hasTag) {
            logger.warn("$prefix Tag $localTag already exists ($hasLocalTag, $hasRemoteTag).")
            if (!isSnapshot) {
                throw buildException("FATAL ERROR: Cannot tag non-snapshot release with an existing tag: $localTag")
            }
            // delete on any remote before push.
            //execute(throwOnError = true, "git", "push", "origin", ":${remoteTag(localTag)}")
        }

        if (isSnapshot) {
            execute(throwOnError = false, "git", "tag", "-d", localTag)
            execute(throwOnError = false, "git", "push", "origin", ":refs/tags/$localTag")
        }
        execute(throwOnError = true, "git", "tag", localTag, localLastCommit)
        execute(throwOnError = true, "git", "push", "origin", "--tags")
// Alternatively git push origin --tags to push all local tag changes or git push origin <tagname>

class GitHubTag {
    val prefix = "GitHubTag"
    val tagPrefix = "v"
    val tagSuffix = "-SNAPSHOT"
    val tagSuffixLength = tagSuffix.length

    fun tagExistsLocal(tag: String): Boolean {
        val (exitCode, _) = project.executeCommand(throwOnError = false, "git", "rev-parse", tag)
        return exitCode == 0
    }

    fun tagExistsRemote(tag: String): Boolean {
        val (exitCode, _) = project.executeCommand(throwOnError = false, "git", "ls-remote", "--tags", "origin", tag)
        return exitCode == 0
    }

    fun deleteTag(tag: String, localOnly: Boolean = true) {
        if (tagExistsLocal(tag)) {
            project.executeCommand(throwOnError = true, "git", "tag", "-d", tag)
            logger.lifecycle("$prefix Deleted local tag: $tag")
        }
        if (!localOnly && tagExistsRemote(tag)) {
            project.executeCommand(throwOnError = true, "git", "push", "origin", ":refs/tags/$tag")
            logger.lifecycle("$prefix Deleted remote tag: $tag")
        }
    }

    fun createTag(tag: String, commit: String) {
        project.executeCommand(throwOnError = true, "git", "tag", tag, commit)
        project.executeCommand(throwOnError = true, "git", "push", "origin", "--tags")
        logger.lifecycle("$prefix Created tag: $tag")
    }

    fun ensureTag() {
        val parsedVersion = File(compositeRootProjectDirectory.asFile, "VERSION").readText().trim()
        if (parsedVersion != version.toString()) {
            throw buildException("$prefix Version mismatch: parsed version '$parsedVersion' does not match project version '$version'")
        }
        val baseVersion = parsedVersion.removeSuffix(tagSuffix)
        val isSnapshot = project.isSnapshot()
        val localTag = buildString {
            append(tagPrefix)
            append(baseVersion)
            if (isSnapshot) {
                append(tagSuffix)
            }
        }
        val remoteTag = "$tagPrefix$baseVersion"
        project.executeCommand(throwOnError = true, "git", "fetch", "--force", "--tags")
        val localBranchName = project.executeCommand(throwOnError = true, "git", "branch", "--show-current").second
        val remote
}

val deleteTag by tasks.registering {
    fun tagExistsLocal(tag: String): String {

    }
    fun deleteTag(tag: String, localOnly: Boolean=true) {

    }


    onlyIf {
        logger.lifecycle("$prefix Delete tag is only used for SNAPSHOT versions.")
        project.isSnapshot()
    }
    doLast {
        TODO("deleter")
    }
}


val en by tasks.registering {
    doLast {
        fun output(vararg args: String) = project.executeCommand(throwOnError = true, *args).second

        fun execute(throwOnError: Boolean = false, vararg args: String): Pair<Int, String> {
            val result = project.executeCommand(throwOnError, *args)
            if (result.first != 0) {
                logger.error("$prefix Git returned non zero value: $result")
            }
            return result
        }

        fun remoteTag(localTag: String): String {
            return "refs/tag/$localTag"
        }

        fun deleteTag(throwOnError: Boolean = false, localTag: String): Boolean {
            // only if tag exists
            val remoteTag = remoteTag(localTag)
            //execute(throwOnError = throwOnError, "git", "tag", "-d", localTag)
            // 1) Delete the tag on any remote before pushing
            execute(throwOnError = throwOnError, "git", "push", "origin", ":${remoteTag(localTag)}")
            logger.lifecycle("$prefix Deleted tags: (local: $localTag, remote: $remoteTag)")
            return true
        }

        fun existingCommits(localTag: String): Pair<String, String> {
            val (localTagValue, localTagCommit) = execute(throwOnError = false, "git", "rev-list", "-n", "1", localTag)
            val (remoteTagValue, remoteTagCommitAndName) = execute(
                throwOnError = false,
                "git",
                "ls-remote",
                "--tags",
                "origin",
                remoteTag(localTag)
            )
            val local = if (localTagValue == 0) localTagCommit else ""
            val remote =
                if (remoteTagValue == 0) remoteTagCommitAndName.removeSuffix(remoteTag(localTag)).trim() else ""
            return local to remote
        }

        val parsedVersion = File(compositeRootProjectDirectory.asFile, "VERSION").readText().trim()
        if (parsedVersion != version.toString()) {
            throw buildException("$prefix Version mismatch: parsed version '$parsedVersion' does not match project version '$version'")
        }
        val baseVersion = parsedVersion.removeSuffix("-SNAPSHOT")
        val isSnapshot = project.isSnapshot()

        val localTag = buildString {
            append(if (isSnapshot) "snapshot/v" else "v")
            append(baseVersion)
        }

        val remoteTag = remoteTag(localTag)
        execute(throwOnError = true, "git", "fetch", "--force", "--tags") // TODO P options?
        val localBranchName = output("git", "branch", "--show-current")
        val remoteBranchName = "remotes/origin/$localBranchName"
        val localLastCommit = output("git", "rev-parse", "HEAD")
        val remoteLastCommit = output("git", "ls-remote", "origin", "HEAD").removeSuffix("HEAD").trim()
        val (localTagCommit, remoteTagCommit) = existingCommits(localTag)
        val hasLocalTag = localTagCommit.isNotEmpty()
        val hasRemoteTag = remoteTagCommit.isNotEmpty()
        val hasTag = hasLocalTag || hasRemoteTag

        logger.lifecycle(
            """
             $prefix createTag for version $version (base version: $baseVersion)
             $prefix   isSnapshot: $isSnapshot
             $prefix   hasLocalTag: $hasLocalTag, hasRemoteTag: $hasRemoteTag
             $prefix   Local branch: '$localBranchName'
             $prefix       last commit: '$localLastCommit'
             $prefix       tag : '$localTag'
             $prefix       tag place at local commit: '$localTagCommit'
             $prefix   Remote branch: '$remoteBranchName'
             $prefix       last: '$remoteLastCommit'
             $prefix       tag : '$remoteTag'
             $prefix       tag placed at remote commit: '$remoteTagCommit'
         """.trimIndent()
        )

        if (hasTag) {
            logger.warn("$prefix Tag $localTag already exists ($hasLocalTag, $hasRemoteTag).")
            if (!isSnapshot) {
                throw buildException("FATAL ERROR: Cannot tag non-snapshot release with an existing tag: $localTag")
            }
            // delete on any remote before push.
            //execute(throwOnError = true, "git", "push", "origin", ":${remoteTag(localTag)}")
        }

        if (isSnapshot) {
            execute(throwOnError = false, "git", "tag", "-d", localTag)
            execute(throwOnError = false, "git", "push", "origin", ":refs/tags/$localTag")
        }
        execute(throwOnError = true, "git", "tag", localTag, localLastCommit)
        execute(throwOnError = true, "git", "push", "origin", "--tags")
// Alternatively git push origin --tags to push all local tag changes or git push origin <tagname>
    }
*/
