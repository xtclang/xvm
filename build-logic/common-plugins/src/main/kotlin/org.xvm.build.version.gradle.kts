/**
 * This is a very thin precompiled script plugin that helps us set the semantic version
 * of all XDK components/subprojects. The plugin itself should not do any manipulation of
 * versions, since it can be applied from arbitrary thord party code.
 *
 * Any XTC project will have an extension with its resolved SemanticVersion
 */

import XdkBuildLogic.Companion.XDK_TASK_GROUP_VERSION
import org.gradle.api.Project

plugins {
    id("org.xvm.build.debug")
}

val semanticVersion by extra {
    initXdkVersion()
}

val versionHandler = xdkBuildLogic.xdkVersionHandler()

// TODO if we can fold these functions into the XdkBuildLogic class, it would be a lot nicer. (TODO: partially done)
fun Project.assignXdkVersion(semanticVersion: SemanticVersion): SemanticVersion {
    val (group, name, version) = semanticVersion

    if (project.name != name) {
        throw buildException("Illegal state: project name '${project.name}' does not match the name in the semantic version: '$name'")
    }

    project.group = group
    project.version = version

    logger.info("$prefix XDK Project '$name' versioned as: '$semanticVersion'")
    logger.info(
        """
        $prefix XDK Project '$name' versioned as: '$semanticVersion'
        $prefix    project.group  : ${project.group}
        $prefix    project.name   : ${project.name}
        $prefix    project.version: ${project.version}
    """.trimIndent()
    )

    return semanticVersion
}

fun Project.initXdkVersion(): SemanticVersion {
    validateGradle()
    return assignXdkVersion(SemanticVersion.resolveCatalogSemanticVersion(project))
}

fun Project.isVersioned(): Boolean {
    return !isNotVersioned()
}

fun Project.isNotVersioned(): Boolean {
    return version == Project.DEFAULT_VERSION
}


val bumpProjectVersion by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one."
    doLast {
        versionHandler.updateVersionCatalogFile(false)
    }
}

val bumpProjectVersionToSnapshot by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one, and suffix the new version string with '-SNAPSHOT'."
    doLast {
        versionHandler.updateVersionCatalogFile(true)
    }
}
