/**
 * This is a very thin precompiled script plugin that helps us set the semantic version
 * of all XDK components/subprojects. The plugin itself should not do any manipulation of
 * versions, since it can be applied from arbitrary thord party code.
 *
 * Any XTC project will have an extension with its resolved SemanticVersion
 */

import XdkBuildLogic.Companion.XDK_TASK_GROUP_VERSION
import org.gradle.api.Project
import java.io.IOException

plugins {
    id("org.xvm.build.debug")
}

val semanticVersion by extra {
    initXdkVersion()
}

val Project.versionCatalogToml: File get() = File("$compositeRootProjectDirectory/gradle/libs.versions.toml")

// TODO if we can fold these functions into the XdkBuildLogic class, it would be a lot nicer. (TODO: partially done)
fun Project.assignXdkVersion(semanticVersion: SemanticVersion): SemanticVersion {
    fun ensureNotVersioned(project: Project): Unit = project.run {
        // Group does not always default to empty. It may default to parent project name hierarchy too.
        val hasGroup = group.toString().isNotEmpty()
        val hasVersion = Project.DEFAULT_VERSION == version.toString()
        if ((hasGroup || hasVersion) && group.toString().indexOf('.') != -1) {
            project.logger.warn("Project '$name' is not expected to have hierarchical group and version configured at init: (version: group='$group', name='$name', version='$version')")
        }
    }
    ensureNotVersioned(project)
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

fun updateVersionCatalogFile(toSnapshot: Boolean) {
    val current = semanticVersion
    val next = current.bump(toSnapshot)
    logger.lifecycle("$prefix '${project.name}' (upgrading '$current' to '$next')")
    versionCatalogToml.updateVersionCatalog(current, next)
    logger.lifecycle("""
        $prefix bumpProjectVersion updated TOML file: '$versionCatalogToml'
        $prefix IMPORTANT: depending on your environment, a full clean and rebuild may be required.
    """.trimIndent())
}

fun File.updateVersionCatalog(current: SemanticVersion, next: SemanticVersion): List<Pair<String, String>> {
    val ls = System.lineSeparator()
    val srcLines = readLines().map { it.trim() }
    val changedLines = mutableListOf<Pair<String, String>>()
    val newLines = buildList {
        var section = ""
        for (it in srcLines) {
            if (it.isEmpty()) {
                add(it)
                continue
            }

            val first = it[0]
            val last = it[it.length - 1]
            if (first == '[' && last == ']') {
                section = it
                add(it)
                continue
            }

            if (section != SemanticVersion.VERSION_CATALOG_TOML_VERSIONS) {
                add(it)
                continue
            }

            when (val split = it.split("=")[0].trim()) {
               SemanticVersion.XDK_VERSION_CATALOG_VERSION, SemanticVersion.XDK_VERSION_CATALOG_PLUGIN_VERSION -> {
                    if (!it.contains(current.artifactVersion)) {
                        throw IOException("ERROR: Failed to find current version '$current' in version catalog entry: $it")
                    }
                    val updated = "$split = \"${next.artifactVersion}\""
                    add(updated)
                    changedLines.add(it to updated)
                }
                else -> add(it)
            }
        }
    }

    if (changedLines.isEmpty()) {
        throw IOException("ERROR: Failed to replace any version strings for '$current' in toml: $absolutePath");
    }

    changedLines.forEach {
        logger.lifecycle("$prefix bumpProjectVersion changed TOML entry ${it.first} to ${it.second}.")
    }

    writeText(newLines.joinToString(ls, postfix = ls))
    return changedLines
}

val bumpProjectVersion by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one."
    doLast {
        updateVersionCatalogFile(false)
    }
}

val bumpProjectVersionToSnapshot by tasks.registering {
    group = XDK_TASK_GROUP_VERSION
    description = "Increase the version of the current XDK build with one, and suffix the new version string with '-SNAPSHOT'."
    doLast {
        updateVersionCatalogFile(true)
    }
}
