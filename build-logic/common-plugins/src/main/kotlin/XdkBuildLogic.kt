import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogLevel.LIFECYCLE
import java.io.File

abstract class XdkProjectBuildLogic(protected val project: Project) {
    protected val logger = project.logger
    protected val prefix = project.prefix

    override fun toString(): String {
        return this::class.simpleName?.let { "$it('${project.name}')" } ?: throw IllegalStateException("Unknown class: ${this::class}")
    }
}

class XdkBuildLogic private constructor(project: Project) : XdkProjectBuildLogic(project) {
    private val xdkGit: GitHubProtocol by lazy {
        // A semantic version must be resolved when this is called, or we will get an exception.
        GitHubProtocol(project)
    }

    private val xdkVersions: XdkVersionHandler by lazy {
        XdkVersionHandler(project)
    }

    private val xdkDistributions: XdkDistribution by lazy {
        XdkDistribution(project)
    }

    private val xdkProperties: XdkProperties by lazy {
        logger.info("$prefix Created lazy XDK Properties for project ${project.name}")
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

    fun gitHubProtocol(): GitHubProtocol {
        return xdkGit
    }

    companion object {
        const val DEFAULT_JAVA_BYTECODE_VERSION = 20 // TODO: We still have to compile to 20 bytecode, because Kotlin 1.9 does not support 21.
        const val XDK_TASK_GROUP_DEBUG = "debug"
        const val XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE = "xdk-distribution-archive"
        const val XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR = "javatools-fatjar"
        const val XDK_ARTIFACT_NAME_MACK_DIR = "mack-dir"

        private val singletonCache: MutableMap<Project, XdkBuildLogic> = mutableMapOf()

        fun instanceFor(project: Project): XdkBuildLogic {
            if (singletonCache.contains(project)) {
                return singletonCache[project]!!
            }

            val instance = XdkBuildLogic(project)
            singletonCache[project] = instance
            project.logger.info(
                    """
                    ${project.prefix} Creating new XdkBuildLogic for project '${project.name}'
                    ${project.prefix} (singletonCache)      ${System.identityHashCode(singletonCache)}
                    ${project.prefix} (project -> instance) ${System.identityHashCode(project)} -> ${System.identityHashCode(instance)}
                """.trimIndent())
            return instance
        }
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

val Project.buildRepoDirectory get() = compositeRootBuildDirectory.dir("repo")

val Project.xdkBuildLogic: XdkBuildLogic get() = XdkBuildLogic.instanceFor(this)

val Project.prefix: String get() = "[$name]"

val Task.prefix: String get() = "[${project.name}:$name]"

// TODO: A little bit hacky: use a config, but there is a mutual dependency between the lib_xtc and javatools.
//  Better to add the resource directory as a source set?
val Project.xdkIconFile: String get() = "$compositeRootProjectDirectory/javatools_launcher/src/main/c/x.ico"

// TODO: A little bit hacky, for same reason as above; Better to add the resource directory as a source set?
val Project.xdkImplicitsPath: String get() = "$compositeRootProjectDirectory/lib_ecstasy/src/main/resources/implicit.x"

val Project.xdkImplicitsFile: File get() = File(xdkImplicitsPath)

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
        logger.info("$prefix Task tunneling property for $key to project. Can be set as input provider.")
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

fun Project.buildException(msg: String, level: LogLevel = LIFECYCLE): Throwable {
    val prefixed = "$prefix $msg"
    logger.log(level, prefixed)
    return GradleException(prefixed)
}

/**
 * Extension method that can be called during the configuration phase, marking its
 * task instance as forever out of date.
 */
fun Task.considerNeverUpToDate() {
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    logger.info("${project.prefix} WARNING: Task '${project.name}:$name' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...")
}

/**
 * Extension method to flag a task as always up to date. Declaring no outputs will
 * cause a task to rerun, even an extended task.
 */
fun Task.considerAlwaqysUpToDate() {
    outputs.upToDateWhen { true }
}

/**
 * Global predicate that any project can use to disable mutating operations, so that we
 * can, e.g. do a test run, and check the log outputs, but without actually modifying
 * anything.
 */
fun Project.isDryRun() = project.findProperty("dryRun")?.toString()?.toBoolean() ?: false

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
