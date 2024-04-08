import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.xdk.versioning")
    id("maven-publish") // TODO: Adding the maven publish plugin here, will always bring with it the PluginMaven publication. We don't always want to use that e.g. for the plugin build. Either reuse the publication there, or find a better way to add the default maven publication.
}

val semanticVersion: SemanticVersion by extra

/**
 * Configure repositories for XDK artifact publication. Currently, we publish the XDK zip "xdkArchive", and
 * the XTC plugin, "xtcPlugin".
 */
publishing {
    repositories {
        logger.info("$prefix Configuring publications for repository mavenLocal().")
        mavenLocal()
        maven {
            name = "GitHub"
            url = uri(project.getXdkProperty("org.xtclang.repo.github.url"))
            credentials {
                username = project.getXdkProperty("org.xtclang.repo.github.user", "xtclang-bot")
                password = project.getXdkProperty("org.xtclang.repo.github.token", System.getenv("GITHUB_TOKEN"))
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name = "The XDK License"
                    url = "https://github.com/xtclang/xvm/tree/master/license"
                }
            }
            developers {
                developer {
                    name = "The XTC Language Organization"
                    email = "info@xtclang.org"
                }
            }
            scm {
                connection = "scm:git:git://github.com/xtclang/xvm.git"
                developerConnection = "scm:git:ssh://github.com/xtclang/xvm.git"
                url = "https://github.com/xtclang/xvm/tree/master"
            }
            withXml {
                val propNode = asNode().appendNode("properties")
                propNode.appendNode("gitCommit", project.executeCommand(throwOnError = true, "git", "rev-parse", "HEAD"))
                propNode.appendNode("xdkVersion", project.version)
            }
        }
    }
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. build and mavenLocal)."
    dependsOn(publishAllPublicationsToMavenLocalRepository)
}

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. build and mavenLocal)."
    dependsOn(publishAllPublicationsToGitHubRepository)
}

val publishAllPublicationsToGitHubRepository by tasks.existing

val publishAllPublicationsToMavenLocalRepository by tasks.existing

val deleteAllLocalPublications by tasks.registering {
    doLast {
        logger.warn("Task '$name' is not implemented yet. Delete your \$HOME/.m2 directory and any other local repositories manually.")
    }
}

val listAllLocalPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists local Maven publications for this project from the mavenLocal() repository."
    doLast {
        logger.lifecycle("$prefix '$name' Listing local publications (and their artifacts) for project '${project.group}:${project.name}':")
        //private val localPublishTasks = provider { tasks.withType<PublishToMavenRepository>().filter{ it.name.contains("Local") }.toList() }
        tasks.withType<PublishToMavenRepository>().filter { it.name.contains("Local") }.forEach {
            with(it.publication) {
                logger.lifecycle("$prefix     '${it.name}' (${artifacts.count()} artifacts):")
                val baseUrl = "${it.repository.url}${groupId.replace('.', '/')}/$artifactId/$version/$artifactId"
                artifacts.forEach { artifact ->
                    val desc = buildString {
                        append(baseUrl)
                        append("-$version")
                        if (artifact.classifier != null) {
                            append("-${artifact.classifier}")
                        }
                        append(".${artifact.extension}")
                    }
                    logger.lifecycle("$prefix         Local Artifact: '$desc'")
                }
            }
        }
    }
}

val listAllRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists publications for this project on the 'xtclang' org GitHub package repo."
    doLast {
        val github = xdkBuildLogic.github()
        logger.lifecycle("$prefix '$name' Listing remote publications for project '${project.group}:${project.name}':")
        val packageNames = github.queryXtcLangPackageNames()
        if (packageNames.isEmpty()) {
            logger.lifecycle("$prefix   No Maven packages found.")
            return@doLast
        }
        packageNames.forEach { pkg ->
            logger.lifecycle("$prefix    Maven package: '$pkg':")
            val versions = github.queryXtcLangPackageVersions(pkg)
            if (versions.isEmpty()) {
                logger.warn("$prefix        WARNING: No versions found for this package. Corrupted package repo?")
                return@forEach
            }
            versions.forEach { logger.lifecycle("$prefix        version: '$it'") }
        }
    }
}

val deleteAllRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description =
        "Delete all versions of all packages on the 'xtclang' org GitHub package repo. WARNING: ALL VERSIONS ARE PURGED."
    doLast {
        val github = xdkBuildLogic.github()
        github.deleteXtcLangPackages() // TODO: Add a pattern that can be set thorugh a property to get finer granularity here than "kill everything!".
        logger.lifecycle("$prefix Finished '$name' deleting publications for project: '${project.group}:${project.name}'.")
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
}