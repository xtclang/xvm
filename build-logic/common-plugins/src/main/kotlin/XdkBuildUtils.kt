import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarFile

val Gradle.rootGradle: Gradle
    get() {
        var dir = this
        while (dir.parent != null) {
            dir = dir.parent!!
        }
        return dir
    }

val Project.rootGradle: Gradle get() = gradle.rootGradle

val Gradle.rootLayout: ProjectLayout get() = rootGradle.rootProject.layout

val Project.compositeRootBuildDirectory get() = gradle.rootLayout.buildDirectory

val Project.compositeRootProjectDirectory get() = gradle.rootLayout.projectDirectory

val Project.prefix get() = "[$name]"

fun Project.buildException(msg: String): Throwable {
    logger.error("$prefix $msg")
    return GradleException(msg)
}

/**
 * Generic function for execute without Gradle exec. See: https://docs.gradle.com/enterprise/tutorials/extending-build-scans/
 */
fun execute(p: String): String? {
    ProcessBuilder(p.split(" ")).start().apply { waitFor() }.inputStream.bufferedReader().use { return it.readText().trim().ifEmpty { null } }
}

/**
 * Execute a command through the project context and its exec configurations.
 */
fun executeCommandFor(project: Project, vararg args: String): String? {
    val output = ByteArrayOutputStream()
    project.exec {
        commandLine(*args)
        standardOutput = output
        isIgnoreExitValue = false
    }
    return output.toString().trim().ifEmpty { null }
}

/**
 * Execute a command through the project context and its exec configurations.
 */
fun Project.executeCommand(vararg args: String): String? {
    return executeCommandFor(this, *args)
}
