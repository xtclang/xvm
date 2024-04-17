import org.gradle.api.Project

data class GitLabel(val project: Project, val semanticVersion: SemanticVersion) {

    data class GitResult(val execResult: Pair<Int, String>) {
        val exitValue: Int = execResult.first
        val output: String = execResult.second

        fun lines(): List<String> = output.lines()
    }

    private val artifactBaseVersion = semanticVersion.artifactVersion.removeSuffix("-SNAPSHOT")
    private val tagPrefix = if (semanticVersion.isSnapshot()) "snapshot/" else ""
    private val localTag = "${tagPrefix}v$artifactBaseVersion"
    private val remoteTag = "refs/tags/$tagPrefix/v${semanticVersion.artifactVersion}"

    private fun fetchTags() {
        git(true, "fetch", "--force", "--tags")
    }

    private fun logGitOutput(header: String, result: GitResult): GitResult = project.run {
        val exitValue = result.exitValue
        logger.lifecycle("$prefix $header (exitValue: $exitValue)")
        if (exitValue != 0) {
            logger.warn("$prefix Git call was non-throwing, but returned non zero value: $exitValue")
        }
        result.lines().forEach { line -> logger.lifecycle("$prefix     output: $line") }
        return result
    }

    // For a snapshot, delete existing tag (if there is one), recreate and attach to latest commit.
    // For a non-snapshot, fail if exists locally or remotely. Otherwise tag attach to latest commit.
    // Push all tag changes to upstream as a separate step.
    fun update(): Unit = project.run {
        fetchTags()
        val localBranchName = git(true, "branch", "--show-current").output
        val remoteBranchName = "remotes/origin/$localBranchName"
        val localLastCommit = git(true, "rev-parse", "HEAD").output
        // should be freshly fetched
        val remoteLastCommit = git(true, "ls-remote", "origin", "HEAD").output.removeSuffix("HEAD").trim()
        val localTags = git(true, "tag", "--list").output.lines().toList()
        val remoteTags = git(true, "ls-remote", "--tags", "origin").output.lines().map {
            it.split("\\s+".toRegex(), 2).last()
        }.toList()

        System.err.println("localtags: " + localTags)
        System.err.println("remotetags: " + remoteTags)

        logger.lifecycle("""
            $prefix createTag for version $semanticVersion
            $prefix    local branch       : $localBranchName
            $prefix    remote branch      : $remoteBranchName
            $prefix:   local last commit  : $localLastCommit
            $prefix:   remote last commit : $remoteLastCommit
        """.trimIndent())

        logger.lifecycle("""
            $prefix:   local tags         : $localTags
            $prefix:   remote tags        : $remoteTags
        """.trimIndent())
        // if this is not a snapshot, the tag cannot already exist. That is a failure
        // if this is a snapshot, delete and recreate teh tag at the last commit.
        if (project.isSnapshot()) {
            // git tag -d localTag
            // git push origin: remoteTag
            //if (tagExistsLocal(localTag)) {
            //    deleteTag(localTag)
            //}
        } else {
            //if
            //git(true, "tag", localTag, localLastCommit)
            //git(true, "push", "origin", "--tags")
        }
        /*execute(throwOnError = true, "git", "fetch", "--force", "--tags") // TODO P options?
        val localBranchName = output("git", "branch", "--show-current")
        val remoteBranchName = "remotes/origin/$localBranchName"
        val localLastCommit = output("git", "rev-parse", "HEAD")
        val remoteLastCommit = output("git", "ls-remote", "origin", "HEAD").removeSuffix("HEAD").trim()
        val (localTagCommit, remoteTagCommit) = existingCommits(localTag)*/

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

    private fun git(throwOnError: Boolean = true, vararg args: String): GitResult = project.run {
        // TODO move this to project independent spawn, but right now github actions hates that.
        val cmd = buildList {
            add("git")
            addAll(args)
        }
        return logGitOutput(cmd.joinToString(" "), GitResult(executeCommand(cmd, throwOnError)))
    }

}

/*
val ensureTag by tasks.registering {
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


val ensureTag by tasks.registering {
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
