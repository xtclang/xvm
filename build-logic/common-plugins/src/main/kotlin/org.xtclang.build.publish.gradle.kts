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
        }
    }
}

val ensureTag by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Ensure that the current commit is tagged with the current version."
    doLast {
        val git = GitLabel(project, semanticVersion)
        git.update()
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