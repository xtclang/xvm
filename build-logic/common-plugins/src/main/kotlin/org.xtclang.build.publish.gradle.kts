import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.version")
    id("maven-publish")
}

internal val xtcGitHubClient = xdkBuildLogic.github()

/**
 * Configure repositories for XDK artifact publication. Currently, we publish the XDK zip "xdkArchive", and
 * the XTC plugin, "xtcPlugin".
 */
publishing {
    repositories {
        logger.info("$prefix Configuring publications for repository mavenLocal().")
        mavenLocal()

        logger.info("$prefix Configuring publications for repository local flat dir: '$buildRepoDirectory'")
        maven {
            name = "build"
            description = "Publish all publications to the local build directory repository."
            url = uri(buildRepoDirectory)
        }

        logger.info("$prefix Configuring publications for xtclang.org GitHub repository.")
        with (xtcGitHubClient) {
            if (verifyGitHubConfig()) {
                logger.info("$prefix Found GitHub package credentials for XTC (url: $uri, user: $user, org: $organization, read-only: $isReadOnly)")
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

val listGitHubPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists publications for this project on the 'xtclang' org GitHub package repo."
    doLast {
        logger.lifecycle("$prefix '$name' Listing publications for project '${project.group}:${project.name}':")
        val packageNames = xtcGitHubClient.queryXtcLangPackageNames()
        if (packageNames.isEmpty()) {
            logger.lifecycle("$prefix   No Maven packages found.")
            return@doLast
        }
        packageNames.forEach { pkg ->
            logger.lifecycle("$prefix    Maven package: '$pkg':")
            val versions = xtcGitHubClient.queryXtcLangPackageVersions(pkg)
            if (versions.isEmpty()) {
                logger.warn("$prefix        WARNING: No versions found for this package. Corrupted package repo?")
                return@forEach
            }
            versions.forEach { logger.lifecycle("$prefix        version: '$it'") }
        }
    }
}

val deleteGitHubPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description =  "Delete all versions of all packages on the 'xtclang' org GitHub package repo. WARNING: ALL VERSIONS ARE PURGED."
    doLast {
        xtcGitHubClient.deleteXtcLangPackages()
        logger.lifecycle("$prefix Finished '$name' deleting publications for project: '${project.group}:${project.name}'.")
    }
}

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. build and mavenLocal)."
    dependsOn(publishAllPublicationsToBuildRepository, publishAllPublicationsToMavenLocalRepository)
}

val pruneBuildRepo by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Helper task called internally to make sure the build repo is wiped out before republishing. Used by installLocalDist and remote publishing only."
    delete(buildRepoDirectory)
}

val publishAllPublicationsToBuildRepository by tasks.existing {
    dependsOn(pruneBuildRepo)
    mustRunAfter(pruneBuildRepo)
}

val publishAllPublicationsToMavenLocalRepository by tasks.existing
