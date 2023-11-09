import GitHubPackages.Rest.GITHUB_PUBLICATION_NAME
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xvm.build.version")
    id("maven-publish")
    id("signing")
}

internal val xtcGitHubClient = xdkBuildLogic.gitHubClient()

/**
 * Configure repositories for XDK artifact publication. Currently we publish the XDK zip "xdkArchive", and
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
        if (xtcGitHubClient.verifyGitHubConfig()) {
            logger.info("$prefix XTC GitHub username: ${xtcGitHubClient.gitHubUser}, organization: ${xtcGitHubClient.gitHubOrganization}")
            val (user, token) = xtcGitHubClient.gitHubCredentials

            maven {
                name = GITHUB_PUBLICATION_NAME
                description = "Publish all publications to the xtclang.org GitHub repository."
                url = uri(xtcGitHubClient.gitHubUrl)
                credentials {
                    username = user
                    password = token
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

tasks.withType<Sign>().configureEach {
    onlyIf {
        xdkBuildLogic.isSnapshot().not() && getXdkPropertyBoolean("org.xvm.publications.sign", false) && System.getenv("CI").isNullOrEmpty() // TODO postpone signing in CI.
    }
    logger.info("$prefix Configuring signature: '$name'")
    val keyId = getXdkProperty("org.xvm.signing.keyId", "")
    val password = getXdkProperty("org.xvm.signing.password", "")
    val secretKeyRingFile = getXdkProperty("org.xvm.signing.secretKeyRingFile", "")
    project.extra["signing.keyId"] = keyId
    project.extra["signing.password"] = password
    project.extra["signing.secretKeyRingFile"] = secretKeyRingFile
}

fun notParallel(taskName: String): Boolean {
    if (xdkBuildLogic.isParallel) {
        logger.warn("$prefix Parallel builds are not supported for '$taskName'. Task disabled.")
        return false
    }
    return true
}

val listGitHubPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists publications for this project on the 'xtclang' org GitHub package repo."
    doLast {
        logger.lifecycle("$prefix Listing publications for project '${project.group}:${project.name}':")
        val packageNames = xtcGitHubClient.queryXtcLangPackageNames()
        if (packageNames.isEmpty()) {
            logger.lifecycle("$prefix   No Maven packages found.")
            return@doLast
        }
        packageNames.forEach {
            logger.lifecycle("$prefix    Maven package: '$it':")
            val versions = xtcGitHubClient.queryXtcLangPackageVersions(it)
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
    description =
        "Delete all versions of all packages on the 'xtclang' org GitHub package repo. WARNING: ALL VERSIONS ARE PURGED."
    doLast {
        logger.lifecycle("Deleting publications for project: '${project.group}:${project.name}'...")
        xtcGitHubClient.deleteXtcLangPackages()
    }
}

val publishAllPublicationsToBuildRepository by tasks.existing {
    dependsOn(pruneBuildRepo)
}

val publishAllPublicationsToMavenLocalRepository by tasks.existing

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. build and mavenLocal)."
    dependsOn(publishAllPublicationsToBuildRepository, publishAllPublicationsToMavenLocalRepository)
}

val pruneBuildRepo by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Helper task called internally to make sure the build repo is wiped out before republishing."
    delete(buildRepoDirectory)
    doLast {
        logger.lifecycle("$prefix Finished $name (deleted build repo under ${buildRepoDirectory.get()}).")
    }
}
