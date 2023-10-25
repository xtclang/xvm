/**
 * This is a very thin precompiled script plugin that helps us set the semantic version
 * of all XDK components/subprojects. The plugin itself should not do any manipulation of
 * versions, since it can be applied from arbitrary thord party code.
 *
 * Any XTC project will have an extension with its resolved SemanticVersion
 */

import XdkBuildLogic.Companion.XDK_TASK_GROUP_VERSION
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.provideDelegate

plugins {
    id("org.xvm.build.debug")
}

initXdkVersion()

// TODO if we can fold these functions into the XdkBuildLogic class, it would be a lot nicer.
fun Project.assignXdkVersion(semanticVersion: SemanticVersion): SemanticVersion {
    checkIsNotVersioned()
    val (group, name, version) = semanticVersion
    if (project.name != name) {
        throw buildException("Illegal state: project name '${project.name}' does not match the name in the semantic version: '$name'")
    }

    project.group = group
    project.version = version

    logger.lifecycle("$prefix XDK Project '$name' versioned as: '$semanticVersion'")
    logger.info(
        """
        $prefix XDK Project '$name' versioned as: '$semanticVersion'
        $prefix    project.group  : ${project.group}
        $prefix    project.name   : ${project.name}
        $prefix    project.version: ${project.version}
    """.trimIndent()
    )

    project.extra.set("org.xvm.project.semanticVersion", semanticVersion)
    return semanticVersion
}

fun Project.initXdkVersion(): SemanticVersion {
    return assignXdkVersion(resolveSemanticVersion())
}

fun Project.checkIsNotVersioned() {
    val hasGroup = project.group.toString().isNotEmpty()
    val hasVersion = Project.DEFAULT_VERSION == project.version.toString()
    if (hasGroup || hasVersion) {
        // Group does not always default to empty. It may default to parent project name too.
        if (group.toString().indexOf('.') != -1) {
            logger.warn("Project '$name' is not expected to have hierarchical group and version configured at init: (version: group='$group', name='$name', version='$version')")
        }
    }
}

fun Project.resolveSemanticVersion(): SemanticVersion {
    return SemanticVersion(
        resolveCatalogVersion(SemanticVersion.XDK_VERSION_CATALOG_GROUP),
        project.name,
        resolveCatalogVersion(SemanticVersion.XDK_VERSION_CATALOG_VERSION)
    )
}

fun Project.resolveCatalogVersion(name: String, catalog: String = "libs"): String = project.run {
    // Why not use typesafe "libs.versions... etc." here? This is why:
    //    When building this precompiled script plugin, we are still partly on the settings evaluation
    //    level, and cannot guarantee that we have access to the soon-to-be-precompiled type safe
    //    VersionCatalog instance yet.
    extensions.findByType<VersionCatalogsExtension>()?.also { catalogs ->
        val versionCatalog = catalogs.named(catalog)
        val value = versionCatalog.findVersion(name)
        if (value.isPresent) {
            return value.get().toString()
        }
    }
    throw buildException("Version catalog entry '$name' has no value for '$catalog:$name'")
}

fun Project.isVersioned(): Boolean {
    return !isNotVersioned()
}

fun Project.isNotVersioned(): Boolean {
    return version == Project.DEFAULT_VERSION
}

fun Project.isSnapshotVersion(): Boolean {
    if (isNotVersioned()) {
        throw buildException("Project is not versioned; XDK subprojects should inherit version from XDK.")
    }
    return version.toString().endsWith("-SNAPSHOT")
}

val xdkVersion by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    doFirst {
        logger.lifecycle("XDK version: ${project.version}")
    }
}

val bumpProjectVersion by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one."
    doFirst {
        TODO("Implement bumpProjectVersion task, and perhaps auto detect and rebuild before executing another publication, if information in version catalog is stale.")
    }
}
