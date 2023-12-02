import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.absolutePathString

class XdkBuildLogic(val project: Project) {
    private val prefix = "[${project.name}]"
    private val logger = project.logger
    private val props = XdkProperties(this)
    private val listener = XdkBuildListener(project)

    @Suppress("unused")
    val isParallel: Boolean get() = project.gradle.startParameter.isParallelProjectExecutionEnabled

    init {
        project.gradle.addBuildListener(listener)
    }

    fun xdkGitHubClient(): GitHubPackages {
        return GitHubPackages(this)
    }

    fun xdkDistribution(): XdkDistribution {
        return XdkDistribution(this)
    }

    fun xdkVersionHandler(): XdkVersionHandler {
        return XdkVersionHandler(this)
    }

    fun isSnapshot(): Boolean {
        return isSnapshot(project)
    }

    fun resolveLocalXdkInstallation(): File {
        return findLocalXdkInstallation() ?: throw project.buildException("Could not find local installation of XVM.")
    }

    fun validateGradle() {
        findExecutableOnPath("gradle")?.let { it ->
            val currentProcess = ProcessHandle.current()
            val hasWrapper = currentProcess.info().arguments().orElse(emptyArray())
                .find { it.contains("gradle") && it.contains("wrapper") }.isNullOrEmpty().not()
            if (!hasWrapper) {
                logger.error(
                    """
                        $prefix Found 'gradle' executable on path: ${it.absolutePathString()}. It also appears you are 
                        $prefix not running through the Gradle wrapper in the root of this repository. This is discouraged!
                        $prefix In order to maintain stable and reproducible builds, please use the Gradle wrapper that
                        $prefix comes with this distribution at all times.
                    """.trimIndent())
            }
        }
    }

    fun getPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
        return props.get(key, defaultValue.toString()).toBoolean()
    }

    fun getProperty(key: String, defaultValue: String? = null): String {
        return props.get(key, defaultValue)
    }

    companion object {
        const val DEFAULT_JAVA_BYTECODE_VERSION = "20"

        const val XDK_TASK_GROUP_DEBUG = "debug"
        const val XDK_TASK_GROUP_VERSION = "version"

        private const val ENV_PATH = "PATH"
        private const val XTC_LAUNCHER = "xec"
        private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

        private const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS" // default "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        // Companion objects are guaranteed to be singletons by Kotlin, so this cache works as build global cache.
        private val cache: MutableMap<Project, XdkBuildLogic> = mutableMapOf()

        fun resolve(project: Project): XdkBuildLogic {
            return cache[project] ?: XdkBuildLogic(project).also { cache[project] = it }
        }

        fun isSnapshot(project: Project): Boolean {
            return project.version.toString().endsWith(SNAPSHOT_SUFFIX)
        }

        fun findExecutableOnPath(executable: String): Path? {
            return System.getenv(ENV_PATH)?.split(File.pathSeparator)?.map { File(it, executable) }
                ?.find { it.exists() && it.canExecute() }?.toPath()?.toRealPath()
        }

        fun findLocalXdkInstallation(): File? {
            return findExecutableOnPath(XTC_LAUNCHER)?.toFile()?.parentFile?.parentFile?.parentFile // xec -> bin -> libexec -> "x.y.z.ppp"
        }

        /**
         * Generate listing of  a directory tree (recursively) with modification timestamps
         * for all its files. This is useful, e.g. for making sure that an installation was
         * updated, and, e.g., not erroneously considered as cached or some other hard-to-debug
         * scenario like that.
         */
        // Helper function to print contents of a directory tree with timestamps
        fun listDirWithTimestamps(dir: File): String {
            val truncate = dir.absolutePath
            return buildString {
                appendLine("Recursively listing '$dir' with modificaton timestamps:")
                dir.walkTopDown().forEach {
                    assert(it.absolutePath.startsWith(truncate))
                    val path = it.absolutePath.substring(truncate.length)
                    val timestamp = getDateTimeStampWithTz(it.lastModified())
                    appendLine("    [$timestamp] '${dir.name}$path'")
                }
            }
        }

        private fun getDateTimeStampWithTz(ms: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault()).format(Date(ms))
        }
    }
}

val Gradle.rootGradle: Gradle
    get() {
        var dir: Gradle? = this
        while (dir!!.parent != null) {
            dir = dir.parent
        }
        return dir
    }

val Gradle.rootLayout: ProjectLayout
    get() = rootGradle.rootProject.layout

val Project.compositeRootProjectDirectory
    get() = gradle.rootLayout.projectDirectory

val Project.compositeRootBuildDirectory
    get() = gradle.rootLayout.buildDirectory

val Project.userInitScriptDirectory
    get() = File(gradle.gradleUserHomeDir, "init.d")

val Project.buildRepoDirectory: Provider<Directory>
    get() = compositeRootBuildDirectory.dir("repo")

val Project.xdkBuildLogic: XdkBuildLogic
    get() = XdkBuildLogic.resolve(this)

val Project.prefix
    get() = "[$name]"

// TODO: Hacky, use a config, but there is a mutual dependency between the lib_xtc and javatools.
//  Better to add the resource directory as a source set?
val Project.xdkImplicitsPath: String
    get() = "$compositeRootProjectDirectory/lib_ecstasy/src/main/resources/implicit.x"

// TODO: Hacky, for same reason as above.
val Project.xtcIconFile: String
    get() = "$compositeRootProjectDirectory/javatools_launcher/src/main/c/x.ico"

fun Project.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = false): Boolean {
    return xdkBuildLogic.getPropertyBoolean(key, defaultValue)
}

fun Project.getXdkProperty(key: String, defaultValue: String? = null): String {
    return xdkBuildLogic.getProperty(key, defaultValue)
}

fun Project.buildException(msg: String): Throwable {
    logger.error("$prefix $msg")
    return GradleException(msg)
}

fun Project.executeCommand(vararg args: String): String? {
    val output = ByteArrayOutputStream()
    val result = project.exec {
        commandLine(*args)
        standardOutput = output
        isIgnoreExitValue = false
    }
    result.assertNormalExitValue()
    return output.toString().trim().ifEmpty { null }
}

/**
 * Extension method that can be called during the configuration phase, marking its
 * task instance as forever out of date.
 */
fun Task.alwaysRerunTask() {
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    logger.info("${project.prefix} WARNING: Task '${project.name}:$name' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...")
}
