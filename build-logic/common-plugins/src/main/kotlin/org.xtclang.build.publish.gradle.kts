import org.gradle.api.publish.plugins.PublishingPlugin.PUBLISH_TASK_GROUP

plugins {
    id("org.xtclang.build.xdk.versioning")
    // TODO: Adding the maven publish plugin here, will always bring with it the PluginMaven publication.
    //  We don't always want to use that e.g. for the plugin build. Either reuse the publication there, or
    //  find a better way to add the default maven publication.
    `maven-publish`
    //id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
}

private val semanticVersion: SemanticVersion by extra
// Extract values during configuration to avoid capturing project references
private val gitHubToken = getXtclangGitHubMavenPackageRepositoryToken()
private val allowPublicationValue = allowPublication()
private val snapshotOnlyValue = snapshotOnly()
private val projectName = project.name
private val projectGroup = project.group.toString()
private val userHome = System.getProperty("user.home")

publishing {
    repositories {
        mavenLocal()
        // Configure GitHub packages repository directly to avoid capturing project references
        if (gitHubToken.isNotEmpty()) {
            maven {
                name = "GitHub"
                url = uri("https://maven.pkg.github.com/xtclang/xvm")
                credentials {
                    username = "xtclang-workflows"
                    password = gitHubToken
                }
            }
        } else {
            logger.warn("[build-logic] WARNING: No GitHub token found, either in config or environment. publishRemote won't work.")
        }
    }
    publications.withType<MavenPublication>().configureEach {
        // Extract publication name to avoid capturing 'this' reference
        val publicationName = name
        logger.info("[build-logic] Configuring publication '$publicationName' for project '$projectName'.")
        pom {
            licenses {
                license {
                    name.set("The XDK License")
                    url.set("https://github.com/xtclang/xvm/tree/master/license")
                }
            }
            developers {
                developer {
                    id.set("xtclang-workflows")
                    name.set("XTC Team")
                    email.set("noreply@xtclang.org")
                }
            }
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        allowPublicationValue
    }
    if (!allowPublicationValue) {
        logger.warn("[build-logic] Skipping publication task, snapshotOnly=${snapshotOnlyValue} for version: '$semanticVersion'")
    }
}

val publishAllPublicationsToMavenLocalRepository by tasks.existing

val publishLocal by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to local repositories (e.g. GitHub and mavenCentral)."
    dependsOn(publishAllPublicationsToMavenLocalRepository)
}

val publishRemote by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "Task that publishes project publications to remote repositories (e.g. mavenLocal)."

    if (gitHubToken.isNotEmpty()) {
        dependsOn("publishAllPublicationsToGitHubRepository")
    }
    doLast {
        if (gitHubToken.isEmpty()) {
            throw GradleException("ERROR: No remote repositories for remote publication are configured.")
        }
    }
}

val listTags by tasks.registering {
    group = PUBLISH_TASK_GROUP
    description = "List all tags in the repository."
    doLast {
        logger.lifecycle("[build-logic] Tag information functionality moved to org.xtclang.build.git convention plugin")
        logger.lifecycle("[build-logic] Use 'resolveGitInfo' task instead")
    }
}

val deleteLocalPublications by tasks.registering(DeleteLocalPublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Delete all local Maven publications for this project from the mavenLocal() repository."
    userHomePath.set(userHome)
    projectName.set(providers.provider { project.name })
}

val listLocalPublications by tasks.registering(ListLocalPublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists local Maven publications for this project from the mavenLocal() repository."
    userHomePath.set(userHome)
    projectName.set(providers.provider { project.name })
    projectGroup.set(providers.provider { project.group.toString() })
}

val listRemotePublications by tasks.registering(ListRemotePublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Task that lists publications for this project on the 'xtclang' org GitHub package repo."
    projectName.set(providers.provider { project.name })
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
val deleteRemotePublications by tasks.registering(DeleteRemotePublicationsTask::class) {
    group = PUBLISH_TASK_GROUP
    description = "Delete all or specific published packages from the xtclang GitHub Maven Repo."
    
    // Extract property values during configuration to avoid capturing project references
    val deleteNamesValue = findProperty("deletePackageNames")?.toString()?.split(",") ?: emptyList()
    val deleteVersionsValue = findProperty("deletePackageVersions")?.toString()?.split(",") ?: emptyList()
    
    deletePackageNames.set(providers.provider { deleteNamesValue })
    deletePackageVersions.set(providers.provider { deleteVersionsValue })
}
