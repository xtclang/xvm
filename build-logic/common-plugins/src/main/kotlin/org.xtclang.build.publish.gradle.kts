import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.xdk.versioning")
    id("maven-publish") // TODO: Adding the maven publish plugin here, will always bring with it the PluginMaven publication. We don't always want to use that e.g. for the plugin build. Either reuse the publication there, or find a better way to add the default maven publication.
}

/*
 * Should we publish the plugin to a common build repository and copy it to any localDist?
 */
private fun shouldPublishPluginToLocalDist(): Boolean {
    return project.getXdkPropertyBoolean("org.xtclang.publish.localDist", false)
}

/**
 * Configure repositories for XDK artifact publication. Currently, we publish the XDK zip "xdkArchive", and
 * the XTC plugin, "xtcPlugin".
 */
publishing {
    repositories {
        logger.info("$prefix Configuring publications for repository mavenLocal().")
        mavenLocal()

        if (shouldPublishPluginToLocalDist()) {
            logger.info("$prefix Configuring publications for repository local flat dir: '$buildRepoDirectory'")
            maven {
                name = "build"
                description = "Publish all publications to the local build directory repository."
                url = uri(buildRepoDirectory)
            }
        }

        logger.info("$prefix Configuring publications for xtclang.org GitHub repository.")
        with (xdkBuildLogic.github()) {
            if (verifyGitHubConfig()) {
                logger.info("$prefix Found GitHub package credentials for XTC (url: $uri, user: $user, org: $organization).")
                maven {
                    name = "GitHub"
                    description = "Publish all publications to the xtclang.org GitHub repository."
                    url = uri(uri)
                    credentials {
                        username = user
                        password = token
                    }
                }
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

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. build and mavenLocal)."
    dependsOn(publishAllPublicationsToMavenLocalRepository)
}

val pruneBuildRepo by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Helper task called internally to make sure the build repo is wiped out before republishing. Used by installLocalDist and remote publishing only."
    if (shouldPublishPluginToLocalDist()) {
        logger.lifecycle("$prefix Installing copy of the plugin to local distribution when it exists.")
        delete(buildRepoDirectory)
    }
}

if (shouldPublishPluginToLocalDist()) {
    logger.warn("$prefix Configuring local distribution plugin publication.")
    val publishAllPublicationsToBuildRepository by tasks.existing {
        dependsOn(pruneBuildRepo)
        mustRunAfter(pruneBuildRepo)
    }
    publishLocal {
        dependsOn(publishAllPublicationsToBuildRepository)
    }
}

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
    description =  "Delete all versions of all packages on the 'xtclang' org GitHub package repo. WARNING: ALL VERSIONS ARE PURGED."
    doLast {
        val github = xdkBuildLogic.github()
        github.deleteXtcLangPackages() // TODO: Add a pattern that can be set thorugh a property to get finer granularity here than "kill everything!".
        logger.lifecycle("$prefix Finished '$name' deleting publications for project: '${project.group}:${project.name}'.")
    }
}
