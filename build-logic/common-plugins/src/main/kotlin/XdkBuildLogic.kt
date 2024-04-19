import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogLevel.LIFECYCLE
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class XdkProjectBuildLogic(protected val project: Project) {
    protected val logger = project.logger
    protected val prefix = project.prefix

    override fun toString(): String {
        return this::class.simpleName?.let { "$it('${project.name}')" } ?: throw IllegalStateException("Unknown class: ${this::class}")
    }
}

class XdkBuildLogic private constructor(project: Project) : XdkProjectBuildLogic(project) {
    private val xdkGit: GitLabel by lazy {
        GitLabel(project, project.findProperty("semanticVersion") as SemanticVersion)
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

    fun git(): GitLabel {
        return xdkGit
    }

    companion object {
        const val DEFAULT_JAVA_BYTECODE_VERSION = 20 // TODO: We still have to compile to 20 bytecode, because Kotlin 1.9 does not support 21.
        const val XDK_TASK_GROUP_DEBUG = "debug"
        const val XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE = "xdk-distribution-archive"
        const val XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR = "javatools-fatjar"
        const val XDK_ARTIFACT_NAME_MACK_DIR = "mack-dir"

        private const val ENV_PATH = "PATH"
        private const val XTC_LAUNCHER = "xec"
        private const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

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

        fun getDateTimeStampWithTz(ms: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault()).format(Date(ms))
        }

        fun findExecutableOnPath(executable: String): Path? {
            return System.getenv(ENV_PATH)?.split(File.pathSeparator)?.map { File(it, executable) }
                ?.find { it.exists() && it.canExecute() }?.toPath()?.toRealPath()
        }

        fun findLocalXdkInstallation(): File? {
            return findExecutableOnPath(XTC_LAUNCHER)?.toFile()?.parentFile?.parentFile // xec -> bin -> xdk
        }

        /**
         * Generate listing of a directory tree (recursively) with modification timestamps
         * for all its files. This is useful, e.g. for making sure that an installation was
         * updated, and, e.g., not erroneously considered as cached or some other hard-to-debug
         * scenario like that.
         */
        fun listDirWithTimestamps(dir: File): String {
            val truncate = dir.absolutePath
            return buildString {
                appendLine("Recursively listing '$dir' with modification timestamps:")
                dir.walkTopDown().forEach {
                    assert(it.absolutePath.startsWith(truncate))
                    val path = it.absolutePath.substring(truncate.length)
                    val timestamp = getDateTimeStampWithTz(it.lastModified())
                    appendLine("    [$timestamp] '${dir.name}$path'")
                }
            }.trim()
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

/*
fun Project.executeCommand(throwOnError: Boolean = false, vararg args: String): Pair<Int, String> = project.run {
    return executeCommand(LIFECYCLE, emptyMap(), throwOnError, args.toList())
}*/

fun Project.executeCommand(
    args: List<String>,
    throwOnError: Boolean = false,
    env: Map<String, String> = emptyMap(),
    logLevel: LogLevel = LIFECYCLE,
    logOutput: Boolean = false,
    dryRun: Boolean = false
): Pair<Int, String> = project.run {

    val cmd = args.joinToString(" ")
    val executable = args.first()
    val cmdPrefix = "$prefix [$executable]"
    val cmdOutputPrefix = "$cmdPrefix [output]"

    logger.log(logLevel, "$cmdPrefix executeCommand (throwOnError=$throwOnError, env=$env): '$cmd'")

    if (dryRun) {
        logger.log(logLevel, "$cmdPrefix Command: '$cmd' would be executed.")
        return 0 to ""
    }

    val os = ByteArrayOutputStream()
    val execResult = exec {
        standardOutput = os
        isIgnoreExitValue = !throwOnError
        environment(env)
        commandLine(args)
    }
    val result = execResult.exitValue to os.toString().trim()

    val (exitValue, output) = result
    if (exitValue != 0) {
        logger.error("$prefix ERROR: Command '$cmd' failed with exit code $exitValue")
        return result
    }

    logger.log(logLevel, "$cmdPrefix Command: '$cmd' executed successfully.")
    if (logOutput) {
        if (output.isEmpty()) {
            logger.log(logLevel, "$cmdOutputPrefix [no output]")
        }
        output.lines().forEach { logger.log(logLevel, "$cmdOutputPrefix $it") }
    }

    return result
}
// TODO these should probably be lazy for input purposes

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
fun Task.considerAlwaysUpToDate() {
    outputs.upToDateWhen { true }
}

fun Project.isSnapshot(): Boolean {
    return project.version.toString().endsWith("-SNAPSHOT")
}