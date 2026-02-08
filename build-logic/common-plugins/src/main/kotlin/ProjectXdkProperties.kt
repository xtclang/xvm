import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.nanoseconds
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

abstract class ProjectXdkProperties @Inject constructor(
    private val providers: ProviderFactory,
    private val service: XdkPropertiesService
) {
    private fun resolve(key: String): String? =
        providers.environmentVariable(toEnvKey(key)).orNull
            ?: providers.gradleProperty(key).orNull
            // Also check underscored version for dotted properties
            // ORG_GRADLE_PROJECT_signing.keyId doesn't work, so CI uses ORG_GRADLE_PROJECT_signing_keyId
            // which creates Gradle property "signing_keyId", not "signing.keyId"
            ?: (if (key.contains('.')) providers.gradleProperty(key.replace('.', '_')).orNull else null)
            ?: providers.systemProperty(key).orNull
            ?: service.get(key)

    fun string(key: String): Provider<String> =
        providers.provider { resolve(key) ?: error("Missing property '$key'") }

    fun string(key: String, default: String): Provider<String> =
        providers.provider { resolve(key) ?: default }

    fun int(key: String): Provider<Int> = string(key).map(String::toInt)

    fun int(key: String, default: Int): Provider<Int> =
        string(key, default.toString()).map(String::toInt)

    fun boolean(key: String): Provider<Boolean> = string(key).map(String::toBoolean)

    fun boolean(key: String, default: Boolean): Provider<Boolean> =
        string(key, default.toString()).map(String::toBoolean)

    fun stringValue(key: String): String = string(key).get()

    fun stringValue(key: String, default: String): String = string(key, default).get()

    fun intValue(key: String): Int = int(key).get()

    fun intValue(key: String, default: Int): Int = int(key, default).get()

    fun booleanValue(key: String): Boolean = boolean(key).get()

    fun booleanValue(key: String, default: Boolean): Boolean = boolean(key, default).get()

    fun has(key: String): Boolean = resolve(key) != null

    fun hasProvider(key: String): Provider<Boolean> = providers.provider { resolve(key) != null }

    /**
     * Convert a property key to environment variable format.
     * Examples:
     *   org.xtclang.publish.github -> ORG_XTCLANG_PUBLISH_GITHUB
     *   githubUsername -> GITHUB_USERNAME
     *   mavenCentralUsername -> MAVEN_CENTRAL_USERNAME
     */
    private fun toEnvKey(key: String): String {
        // First replace dots with underscores
        val withUnderscores = key.replace('.', '_')
        // Then insert underscores before uppercase letters in camelCase
        val withCamelCase = withUnderscores.replace(Regex("([a-z])([A-Z])")) { matchResult ->
            "${matchResult.groupValues[1]}_${matchResult.groupValues[2]}"
        }
        return withCamelCase.uppercase()
    }
}

/**
 * Typed extension accessor for ProjectXdkProperties.
 * Use this in build scripts to access properties with Provider API.
 * Example: val jdk = xdkProperties.int("org.xtclang.java.jdk")
 */
val Project.xdkProperties: ProjectXdkProperties
    get() = extensions.getByType(ProjectXdkProperties::class.java)

/**
 * Semantic version accessor (group:name:version).
 */
val Project.semanticVersion: String
    get() = "$group:$name:$version"


// The doLastTask/doFirstTask extensions would be useful for cases where you're in a nested lambda inside a
// task block and want to ensure you're using the task's logger, but they're not necessary for the straightforward
// cases already in the codebase.
//
// If we are in a val taskName by registering/existing block, we will be configuration cache compatible anyway
// since we are delegating to the task, not to the build script fields like e.g. loggers.
fun Task.doFirstTask(block: Task.() -> Unit) {
    doFirst(Action { block(this) })
}

fun Task.doLastTask(block: Task.() -> Unit) {
    doLast(Action { block(this) })
}

/**
 * Add lifecycle logging with timing around a task's execution. Logs [startMsg] before the task
 * action runs, then calls [endMsg] with the elapsed duration after the task action completes.
 *
 * Uses system properties for timer state between doFirst/doLast (configuration-cache safe).
 * All parameters must be captured as local vals at configuration time (no script-level references).
 *
 * Example:
 * ```
 * val downloadFoo by tasks.registering(Download::class) {
 *     val url = "https://example.com/foo.tar.gz"
 *     val dest = destDir.get().asFile.absolutePath
 *     // ...
 *     logTimed("[foo] Downloading $url...") { elapsed ->
 *         val size = File(dest).length().humanSize()
 *         "[foo] Download complete ($size in $elapsed) -> $dest"
 *     }
 * }
 * ```
 */
fun Task.logTimed(startMsg: String, endMsg: (kotlin.time.Duration) -> String) {
    val key = "gradle.task.timer.${path.replace(":", ".")}"
    doFirstTask {
        System.setProperty(key, System.nanoTime().toString())
        logger.lifecycle(startMsg)
    }
    doLastTask {
        val start = System.getProperty(key)?.toLongOrNull() ?: System.nanoTime()
        val elapsed = (System.nanoTime() - start).nanoseconds
        System.clearProperty(key)
        logger.lifecycle(endMsg(elapsed))
    }
}

/** Human-readable file size (e.g., "12 KB", "6 MB"). */
fun Long.humanSize(): String =
    when {
        this >= 1L shl 20 -> "${this shr 20} MB"
        this >= 1L shl 10 -> "${this shr 10} KB"
        else -> "$this B"
    }

/**
 * Log files in a directory with sizes at lifecycle level.
 * [rootDir] must be captured at configuration time for CC safety.
 */
fun Task.logCopiedFiles(
    tag: String,
    dir: File,
    rootDir: File,
    vararg notes: String,
) {
    val files = dir.listFiles().orEmpty()
    val relPath = dir.relativeTo(rootDir)
    logger.lifecycle("[$tag] ${files.size} file(s) -> $relPath/ (${files.sumOf { it.length() }.humanSize()})")
    files.forEach { f ->
        logger.lifecycle("[$tag]   ${f.name} (${f.length().humanSize()})")
    }
    notes.forEach { logger.lifecycle("[$tag]   $it") }
}
