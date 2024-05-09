import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.xdk.versioning")
    // TODO: Adding the maven publish plugin here, will always bring with it the PluginMaven publication.
    //  We don't always want to use that e.g. for the plugin build. Either reuse the publication there, or
    //  find a better way to add the default maven publication.
    `maven-publish`
    //id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
}

val semanticVersion: SemanticVersion by extra

publishing {
    repositories {
        mavenLocal()
        mavenGitHubPackages(project)
    }
    configureMavenPublications(project)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        allowPublication()
    }
    if (!allowPublication()) {
        logger.warn("$prefix Skipping publication task, snapshotOnly=${snapshotOnly()} for version: '$semanticVersion'")
    }
}

val publishAllPublicationsToMavenLocalRepository by tasks.existing

val publishAllPublicationsToGitHubRepository by tasks.existing

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. GitHub and mavenCentral)."
    dependsOn(publishAllPublicationsToMavenLocalRepository)
}

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to remote repositories (e.g. mavenLocal)."
    dependsOn(publishAllPublicationsToGitHubRepository)
}

val listTags by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "List all tags in the repository."
    doLast {
        logger.lifecycle("$prefix Tag information retrieved from git:")
        val github = xdkBuildLogic.gitHubProtocol()
        github.resolveTags().toString().lines().forEach {
            logger.lifecycle("$prefix     $it")
        }
    }
}

val deleteLocalPublications by tasks.registering {
    doLast {
        val repoDir = File(System.getProperty("user.home"), ".m2/repository/org/xtclang/${project.name}")
        if (!repoDir.exists()) {
            logger.warn("$prefix No local publications found in '${repoDir.absolutePath}'.")
            return@doLast
        }

        val xtclangDir = repoDir.parentFile
        require(xtclangDir.exists() && xtclangDir.isDirectory) {
            "Illegal state: parent directory of '$repoDir' does not exist."
        }
        logger.lifecycle("$prefix Deleting all local publications in '${repoDir.absolutePath}'.")
        delete(repoDir)
        val xtclangFiles = xtclangDir.listFiles()
        if (xtclangFiles == null || xtclangFiles.isEmpty()) {
            logger.lifecycle("$prefix Deleting empty parent directory '${xtclangDir.absolutePath}'.")
            delete(xtclangDir)
        }
    }
}

val listLocalPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists local Maven publications for this project from the mavenLocal() repository."
    doLast {
        logger.lifecycle("$prefix '$name' Listing local publications (and their artifacts) for project '${project.group}:${project.name}':")
        val repoDir = File(System.getProperty("user.home"), ".m2/repository/org/xtclang/${project.name}")
        if (!repoDir.exists()) {
            logger.warn("$prefix WARNING: No local publications found on disk at: '${repoDir.absolutePath}'.")
        }

        logger.lifecycle("$prefix Declared local publication destinations (all may not be published yet) are:")
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

val listRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists publications for this project on the 'xtclang' org GitHub package repo."
    doLast {
        val github = xdkBuildLogic.gitHubProtocol()
        val artifactNames = tasks.withType<PublishToMavenRepository>()
            .filter { !it.name.contains("Local") }
            .map { it.publication }
            .map { "${it.groupId}.${it.artifactId}" }
            .toSet()
        val map = github.resolvePackages(artifactNames)
        if (map.isEmpty()) {
            logger.warn("$prefix No packages found for project '${project.name}'.")
            return@doLast
        }
        logger.lifecycle("$prefix Listing all published packages for project '${project.name}':")
        map.forEach { (packageName, versionMap) ->
            logger.lifecycle("$prefix     Package: '$packageName'")
            versionMap.forEach { (key, timestamps) ->
                val (versionName, versionId) = key
                logger.lifecycle("$prefix         Version: '$versionName (id: $versionId)")
                timestamps.forEach { timestamp -> logger.lifecycle("$prefix             Created at: $timestamp") }
            }
        }
    }
}

/**
 * Delete specified (or all) packages/package versions from the remote side, where allowed.
 * For example, it is allowed on GitHub for the xtclang.org package repo, but not on gradlePluginPortal
 * and mavenCentral, at least not after a specific amount of time. This is the same piece of logic
 * for all remote publications, though.
 *
 * The properties "deletePackageNames" and "deletePackageVersions" can be supplied to
 * only delete a subset of published packages. The value of these properties should
 * be a comma separated list of package names and package versions, respectively.
 *
 * For example:
 *     gradlew deleteRemotePublications -PdeletePackageNames=xdk,xtc-plugin -PdeletePackageVersions=0.4.4-SNAPSHOT
 *
 * There is also a -PdryRun property, which will likely grow into a project wide way of knowing
 * whether to do mutating operations or not during the build.
 */
val deleteRemotePublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Delete all or specific published packages from the xtclang GitHub Maven Repo."
    doLast {
        fun deletePackageProperties(name: String) =
            properties["deletePackage$name"]?.toString()?.split(",") ?: emptyList()

        val github = xdkBuildLogic.gitHubProtocol()
        val deleteNames = deletePackageProperties("Names")
        val deleteVersions = deletePackageProperties("Versions")
        github.deletePackages(deleteNames, deleteVersions)
    }
}
