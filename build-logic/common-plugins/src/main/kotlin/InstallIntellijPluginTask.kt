import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Installs a plugin zip, and the Marketplace plugins it depends on, into an installed IntelliJ IDE, replacing an
 * earlier install of it. The IDE must not be running. It is driven through the IDE's own `installPlugins` command,
 * with the launcher the IDE's product-info.json names for this OS, so nothing about the IDE's layout or its
 * directories is assumed.
 *
 * No IDE command names the plugins directory, but the IDE logs it at startup, so a bare `installPlugins` with only
 * its log redirected reports it, and refuses if the IDE is running; [pluginsDirectory] overrides what it reports.
 * `installPlugins` then installs the dependencies. The plugin itself is unpacked into that directory, since
 * `installPlugins` skips a plugin that is installed in any version, and prefers a newer one from any channel the IDE
 * subscribes to over the zip it is given.
 */
@UntrackedTask(because = "It installs into an IDE outside the build")
abstract class InstallIntellijPluginTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
    private val archiveOperations: ArchiveOperations,
) : DefaultTask() {
    /** The IDE's installation: the .app on macOS, its installation directory elsewhere. */
    @get:Input
    @get:Optional
    abstract val ideHome: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pluginZip: RegularFileProperty

    /** Marketplace plugins the plugin depends on, which `installPlugins` installs only when named. */
    @get:Input
    abstract val dependencyIds: ListProperty<String>

    @get:Input
    abstract val sinceBuild: Property<String>

    @get:Input
    @get:Optional
    abstract val pluginsDirectory: Property<String>

    @get:Internal
    abstract val workDirectory: DirectoryProperty

    @TaskAction
    fun install() {
        val home = File(
            ideHome.orNull ?: throw GradleException(
                "Name the IntelliJ IDE to install into with org.xtclang.idea.home, for example in " +
                    "~/.gradle/gradle.properties:\n  org.xtclang.idea.home=/Applications/IntelliJ IDEA.app",
            ),
        )
        val ide = IntellijInstallation.of(home)
        if (!IntellijInstallation.isAtLeast(ide.build, sinceBuild.get())) {
            throw GradleException("${ide.name} ${ide.build} at $home is older than the plugin's since-build ${sinceBuild.get()}")
        }

        // The probe also refuses a running IDE, so it runs even when the plugins directory is named, before anything is deleted
        val work = workDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
        val logDir = work.resolve("probe-log")
        run(ide, emptyList(), "-Didea.log.path=\"${logDir.absolutePath}\"")
        val pluginsDir = File(
            pluginsDirectory.orNull ?: IntellijInstallation.pluginsPathFromLog(logDir.resolve("idea.log"))
                ?: throw GradleException("${ide.name} did not log its plugins directory in $logDir; name it with org.xtclang.idea.pluginsDir"),
        )

        // Dependencies first, so that a failure leaves the earlier install in place; installPlugins skips installed ones
        if (dependencyIds.get().isNotEmpty()) {
            logger.info(run(ide, dependencyIds.get() + "--give-consent-to-use-third-party-plugins"))
        }

        // The plugin itself is unpacked here: installPlugins would take a newer version of it from any channel the IDE
        // subscribes to. This does what the IDE does with a plugin zip, replacing the directory at its root whole
        val zip = pluginZip.get().asFile
        val installed = pluginsDir.resolve(IntellijInstallation.rootEntry(zip))
        fileSystemOperations.delete { delete(installed) }
        fileSystemOperations.copy {
            from(archiveOperations.zipTree(zip))
            into(pluginsDir)
        }
        logger.lifecycle("Installed ${zip.name} into ${ide.name} ${ide.build}: $installed")
    }

    /** Runs `installPlugins` with [args], and returns its output; fails if the IDE is running or the command fails. */
    private fun run(ide: IntellijInstallation, args: List<String>, jvmOption: String? = null): String {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            executable = ide.launcher.absolutePath
            args(listOf("installPlugins") + args)
            if (jvmOption != null) {
                environment("JAVA_TOOL_OPTIONS", listOfNotNull(System.getenv("JAVA_TOOL_OPTIONS"), jvmOption).joinToString(" "))
            }
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8).trim()
        if ("Only one instance" in text) {
            throw GradleException("${ide.name} is running; quit it first, then install again")
        }
        if (result.exitValue != 0) {
            throw GradleException("${ide.launcher} installPlugins failed (exit ${result.exitValue}):\n$text")
        }
        return text
    }
}

/** An installed IntelliJ IDE, as its product-info.json describes it. */
internal data class IntellijInstallation(val name: String, val build: String, val launcher: File) {
    companion object {
        fun of(home: File): IntellijInstallation {
            val productInfo = home.walk().maxDepth(3).firstOrNull { it.name == "product-info.json" }
                ?: throw GradleException("No product-info.json in $home; org.xtclang.idea.home must name an IntelliJ installation")
            return of(productInfo, System.getProperty("os.name"), System.getProperty("os.arch"))
        }

        fun of(productInfo: File, osName: String, osArch: String): IntellijInstallation {
            val json = JsonSlurper().parse(productInfo) as Map<*, *>
            val os = when {
                osName.startsWith("Mac") -> "macOS"
                osName.startsWith("Windows") -> "Windows"
                else -> "Linux"
            }
            val arch = if (osArch == "x86_64") "amd64" else osArch
            val launches = (json["launch"] as List<*>).map { it as Map<*, *> }.filter { it["os"] == os }
            val launch = launches.firstOrNull { it["arch"] == arch } ?: launches.firstOrNull()
                ?: throw GradleException("$productInfo names no launcher for $os")
            val launcher = productInfo.parentFile.resolve(launch["launcherPath"] as String).normalize()
            return IntellijInstallation(json["name"] as String, json["buildNumber"] as String, launcher)
        }

        /** Whether build number [build] is [since] or later, compared component by component. */
        fun isAtLeast(build: String, since: String): Boolean {
            val actual = build.split('.').map { it.toIntOrNull() ?: 0 }
            val required = since.split('.').map { it.toIntOrNull() ?: 0 }
            val difference = required.indices.map { actual.getOrElse(it) { 0 } - required[it] }.firstOrNull { it != 0 }
            return (difference ?: 0) >= 0
        }

        private val pluginsPathLine = Regex("""^\s+idea\.plugins\.path=(.+?)(?: -> .*)?$""")

        /** The plugins directory the IDE logged at its last startup; it adds " -> exception" when the directory is missing. */
        fun pluginsPathFromLog(log: File): String? =
            log.takeIf(File::isFile)?.readLines()
                ?.mapNotNull { pluginsPathLine.find(it)?.groupValues?.get(1) }
                ?.lastOrNull()

        /** The directory a plugin zip unpacks into, which the IDE replaces whole when it installs the zip. */
        fun rootEntry(zip: File): String =
            ZipFile(zip).use { it.entries().asSequence().map { entry -> entry.name.substringBefore('/') }.first() }
                .also { require(it.isNotEmpty() && it != "." && it != "..") { "$zip has no plugin directory at its root" } }
    }
}
