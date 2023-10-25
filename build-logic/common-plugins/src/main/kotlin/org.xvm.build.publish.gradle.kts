import GitHubPackages.Rest.GITHUB_PUBLICATION_NAME
import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xvm.build.version")
    id("maven-publish")
}

internal val xtcGitHubClient = xdkBuildLogic.gitHubClient()

/**
 * Configure repositories to publish artifacts to.
 */
publishing {
    repositories {
        logger.info("$prefix Configuring publications for repository mavenLocal().")
        mavenLocal()
    }
}

if (xtcGitHubClient.verifyGitHubConfig()) {
    logger.info("$prefix Publication repository (GitHub) at: $xtcGitHubClient.")
    publishing.repositories {
        maven {
            name = GITHUB_PUBLICATION_NAME
            url = uri(xtcGitHubClient.gitHubUrl)
            credentials {
                logger.info("$prefix XTC GitHub username: ${xtcGitHubClient.gitHubUser}, organization: ${xtcGitHubClient.gitHubOrganization}")
                username = xtcGitHubClient.gitHubUser + "."
                password = xtcGitHubClient.gitHubToken
            }
        }
    }
} else {
    logger.warn("$prefix GitHub credentials are invalid, or not configured; publications to GitHub will be disabled.")
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
            logger.lifecycle("$prefix   Maven package: '$it'")
        }
    }
}

val deleteGitHubPublications by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Delete all versions of all packages on the 'xtclang' org GitHub package repo. WARNING: ALL VERSIONS ARE PURGED."
    doLast {
        logger.lifecycle("Deleting publications for project: '${project.group}:${project.name}'...")
        xtcGitHubClient.deleteXtcLangPackages()
    }
}
