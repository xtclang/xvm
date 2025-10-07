import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle

/**
 * Centralized constants and utilities for XDK build logic.
 * Simplified to remove unnecessary layers - use xdkProperties for version info.
 */
object XdkBuildLogic {
    /** Artifact type for XDK distribution archives (tar/zip) */
    const val XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE = "xdk-distribution-archive"

    /** Artifact type for javatools fat jar */
    const val XDK_ARTIFACT_NAME_JAVATOOLS_JAR = "javatools-jar"

    /** Artifact type for Mack directory */
    const val XDK_ARTIFACT_NAME_MACK_DIR = "mack-dir"
}

// TODO: Can we move these guys to the versions handler?
val Gradle.rootGradle: Gradle
    get() {
        var dir: Gradle? = this
        while (dir!!.parent != null) {
            dir = dir.parent
        }
        return dir
    }

val Gradle.rootLayout: ProjectLayout get() = rootGradle.rootProject.layout

val Project.compositeRootProjectDirectory: Directory get() = gradle.rootLayout.projectDirectory

val Project.compositeRootBuildDirectory: DirectoryProperty get() = gradle.rootLayout.buildDirectory

/**
 * Typed extension accessor for ProjectXdkProperties.
 * Use this in build scripts to access properties with Provider API.
 * Example: val jdk = xdkProperties.int("org.xtclang.java.jdk")
 */
val Project.xdkProperties: ProjectXdkProperties
    get() = extensions.getByType(ProjectXdkProperties::class.java)

/**
 * Semantic version accessor (group:name:version).
 * Projects must apply versioning plugin first.
 */
val Project.semanticVersion: String
    get() = "$group:$name:$version"

/**
 * Helper to create XdkDistribution for distribution tasks.
 * Configuration-cache compatible - extracts values immediately.
 */
fun Project.xdkDistribution(): XdkDistribution = XdkDistribution(
    distributionName = name,
    distributionVersion = version.toString(),
    targetArch = XdkDistribution.getCurrentArch(this)
)

