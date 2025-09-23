import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import java.io.File

abstract class XdkProjectBuildLogic(protected val project: Project) {
    protected val logger = project.logger

    override fun toString(): String {
        return this::class.simpleName?.let { "$it('${project.name}')" } ?: throw IllegalStateException("Unknown class: ${this::class}")
    }
}

class XdkBuildLogic(project: Project) : XdkProjectBuildLogic(project) {

    private val xdkVersions: XdkVersionHandler by lazy {
        XdkVersionHandler(project)
    }

    private val xdkDistributions: XdkDistribution by lazy {
        XdkDistribution(project)
    }

    private val xdkProperties: XdkProperties by lazy {
        logger.info("[build-logic] Created lazy XDK Properties for project ${project.name}")
        XdkPropertiesImpl(project)
    }

    fun props(): XdkProperties {
        return xdkProperties
    }

    fun versions(): XdkVersionHandler {
        return xdkVersions
    }

    fun distro(): XdkDistribution {
        return xdkDistributions
    }

    companion object {
        const val XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE = "xdk-distribution-archive"
        const val XDK_ARTIFACT_NAME_JAVATOOLS_JAR = "javatools-jar"
        const val XDK_ARTIFACT_NAME_MACK_DIR = "mack-dir"
    }
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

val Project.userInitScriptDirectory: File get() = File(gradle.gradleUserHomeDir, "init.d")

val Project.xdkBuildLogic: XdkBuildLogic get() = XdkBuildLogic(this)

fun Project.isXdkPropertySet(key: String): Boolean {
    return xdkBuildLogic.props().has(key)
}

fun Project.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
    return xdkBuildLogic.props().get(key, defaultValue)
}

fun Project.getXdkPropertyInt(key: String, defaultValue: Int? = null): Int {
    return xdkBuildLogic.props().get(key, defaultValue)
}

fun Project.getXdkProperty(key: String, defaultValue: String? = null): String {
    return xdkBuildLogic.props().get(key, defaultValue)
}

private fun <T> registerXdkPropertyInput(task: Task, key: String, value: T): T {
    with(task) {
        logger.info("[build-logic] Task tunneling property for $key to project. Can be set as input provider.")
    }
    return value
}

fun Task.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
    return registerXdkPropertyInput(this, key, project.getXdkPropertyBoolean(key, defaultValue))
}

fun Task.getXdkPropertyInt(key: String, defaultValue: Int? = null): Int {
    return registerXdkPropertyInput(this, key, project.getXdkPropertyInt(key, defaultValue))
}

fun Task.getXdkProperty(key: String, defaultValue: String? = null): String {
    return registerXdkPropertyInput(this, key, project.getXdkProperty(key, defaultValue))
}

fun Project.isSnapshot(): Boolean {
    return project.version.toString().endsWith("-SNAPSHOT")
}

fun Project.isRelease(): Boolean {
    return !isSnapshot()
}

fun Project.snapshotOnly(): Boolean {
    return findProperty("snapshotOnly")?.toString()?.toBoolean() ?: false
}

fun Project.allowPublication(): Boolean {
    val forbidPublication = findProperty("forbidPublication")?.toString()?.toBoolean() ?: false
    return !forbidPublication && !(snapshotOnly() && !project.isSnapshot())
}
