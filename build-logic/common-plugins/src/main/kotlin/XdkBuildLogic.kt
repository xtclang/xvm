import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named
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

class XdkBuildLogic(project: Project) : XdkProjectBuildLogic(project) {
    companion object {
        const val DEFAULT_JAVA_BYTECODE_VERSION = 20
        const val XDK_TASK_GROUP_DEBUG = "debug"

        // Artifact names for configuration artifacts. Gradle uses these during the build to identify various
        // non-default style artifacts, that we use as part of resolvable and consumable configurations.
        const val XDK_ARTIFACT_NAME_DISTRIBUTION_ARCHIVE = "xdk-distribution-archive"
        const val XDK_ARTIFACT_NAME_JAVATOOLS_FATJAR = "javatools-fatjar"
        const val XDK_ARTIFACT_NAME_MACK_DIR = "mack-dir"

        private const val ENV_PATH = "PATH"
        private const val XTC_LAUNCHER = "xec"
        private const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        private val cache: MutableMap<Project, XdkBuildLogic> = mutableMapOf()

        fun resolve(project: Project): XdkBuildLogic {
            // Companion objects are guaranteed to be singletons by Kotlin, so this cache works as build global cache.
            return cache[project] ?: XdkBuildLogic(project).also {
                cache[project] = it
                val logger = project.logger
                val size = cache.size
                val uniqueSize = cache.values.distinct().size
                logger.info("${it.prefix} XDK build logic initialized (instances: ${size})")
                assert(size == uniqueSize)
            }
        }

        fun executeCommand(project: Project, vararg args: String): String = project.run {
            val output = ByteArrayOutputStream()
            val result = project.exec {
                commandLine(*args)
                standardOutput = output
                isIgnoreExitValue = false
            }
            if (result.exitValue != 0) {
                logger.error("$prefix ERROR: Command '${args.joinToString(" ")}' failed with exit code ${result.exitValue}")
                return ""
            }
            return output.toString().trim()
        }

        fun getDateTimeStampWithTz(ms: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault()).format(Date(ms))
        }

        fun findExecutableOnPath(executable: String): Path? {
            return System.getenv(ENV_PATH)?.split(File.pathSeparator)?.map { File(it, executable) }
                ?.find { it.exists() && it.canExecute() }?.toPath()?.toRealPath()
        }

        fun findLocalXdkInstallation(): File? {
            return findExecutableOnPath(XTC_LAUNCHER)?.toFile()?.parentFile?.parentFile?.parentFile // xec -> bin -> libexec -> "x.y.z.ppp"
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

    private val xdkProperties = XdkProperties(project)

    private val xdkGitHub: GitHubPackages by lazy {
        GitHubPackages(project)
    }

    private val xdkVersions: XdkVersionHandler by lazy {
        XdkVersionHandler(project)
    }

    private val xdkDistributions: XdkDistribution by lazy {
        XdkDistribution(project)
    }

    fun versions(): XdkVersionHandler {
        return xdkVersions
    }

    fun distro(): XdkDistribution {
        return xdkDistributions
    }

    fun github(): GitHubPackages {
        return xdkGitHub
    }

    fun resolveLocalXdkInstallation(): File {
        return findLocalXdkInstallation() ?: throw project.buildException("Could not find local installation of XVM.")
    }

    fun rebuildUnicode(): Boolean {
        return xdkPropBool("org.xtclang.unicode.rebuild", false)
    }

    internal fun xdkPropIsSet(key: String): Boolean {
        return xdkProperties.has(key)
    }

    internal fun xdkPropBool(key: String, defaultValue: Boolean? = null): Boolean {
        return xdkProperties.get(key, defaultValue?.toString() ?: "false").toBoolean()
    }

    internal fun xdkPropInt(key: String, defaultValue: Int? = null): Int {
        return xdkProperties.get(key, defaultValue?.toString() ?: "0").toInt()
    }

    internal fun xdkProp(key: String, defaultValue: String? = null): String {
        return xdkProperties.get(key, defaultValue)
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

val Project.xdkBuild: XdkBuildLogic
    get() = XdkBuildLogic.resolve(this)

val Project.prefix
    get() = "[$name]"

val Task.prefix
    get() = "[${project.name}:$name]"

// TODO: A little bit hacky: use a config, but there is a mutual dependency between the lib_xtc and javatools.
//  Better to add the resource directory as a source set?
val Project.xdkIconFile: String
    get() = "$compositeRootProjectDirectory/javatools_launcher/src/main/c/x.ico"

// TODO: A little bit hacky, for same reason as above.
//  Better to add the resource directory as a source set?
val Project.xdkImplicitsPath: String
    get() = "$compositeRootProjectDirectory/lib_ecstasy/src/main/resources/implicit.x"

val Project.xdkImplicitsFile: File
    get() = File(xdkImplicitsPath)

fun Project.isXdkPropertySet(key: String): Boolean {
    return xdkBuild.xdkPropIsSet(key)
}

fun Project.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
    return xdkBuild.xdkPropBool(key, defaultValue)
}

fun Project.getXdkPropertyInt(key: String, defaultValue: Int? = null): Int {
    return xdkBuild.xdkPropInt(key, defaultValue)
}

fun Project.getXdkProperty(key: String, defaultValue: String? = null): String {
    return xdkBuild.xdkProp(key, defaultValue)
}

fun Task.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
    logger.info("$prefix Task tunneling property for $key to project")
    return project.getXdkPropertyBoolean(key, defaultValue)
}

fun Task.getXdkPropertyInt(key: String, defaultValue: Int? = null): Int {
    logger.info("$prefix Task tunneling property for $key to project")
    return project.getXdkPropertyInt(key, defaultValue)
}

fun Task.getXdkProperty(key: String, defaultValue: String? = null): String {
    logger.info("$prefix Task tunneling property for $key to project")
    return project.getXdkProperty(key, defaultValue)
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
fun Task.alwaysRerunTask() {
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    logger.info("${project.prefix} WARNING: Task '${project.name}:$name' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...")
}

/**
 * Extension method to flag a task as always up to date. Declaring no outputs will
 * cause a task to rerun, even an extended task.
 */
fun Task.noOutputs() {
    outputs.upToDateWhen { true }
}
