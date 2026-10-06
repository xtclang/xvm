import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes
import kotlin.concurrent.thread

@UntrackedTask(because = "Runtime reporting task reads live sandbox state and should never be state-tracked")
abstract class RunIdeEnvironmentReportTask : DefaultTask() {
    @get:Input
    abstract val ideVersion: Property<String>

    @get:Input
    abstract val sinceBuild: Property<String>

    @get:Input
    abstract val lsp4ijVersion: Property<String>

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val semanticTokensEnabled: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sandboxDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mavenLocalRoot: DirectoryProperty

    @get:Input
    abstract val pluginNames: ListProperty<String>

    @get:Input
    abstract val lspLogDirectory: Property<String>

    @TaskAction
    fun report() {
        val sandbox = sandboxDir.get().asFile
        val ideaLog = sandbox.resolve("log/idea.log")
        val systemDir = sandbox.resolve("system")
        val sandboxIsReused = systemDir.exists() && systemDir.listFiles().orEmpty().isNotEmpty()
        val xtcArtifacts = mavenLocalRoot.get().asFile.resolve("org/xtclang")

        logger.lifecycle("[runIde] ─── Version Matrix (gradle/libs.versions.toml) ───")
        logger.lifecycle("[runIde]   IntelliJ IDEA: ${ideVersion.get()} (sinceBuild=${sinceBuild.get()})")
        logger.lifecycle("[runIde]   LSP4IJ:        ${lsp4ijVersion.get()}")
        logger.lifecycle("[runIde]   XTC plugin:    ${pluginVersion.get()}")
        logger.lifecycle("[runIde]   LSP semantic tokens in IDE: ${semanticTokensEnabled.get()}")

        logger.lifecycle("[runIde] ─── Sandbox ───")
        logger.lifecycle("[runIde]   Path:      ${sandbox.absolutePath}")
        logger.lifecycle(
            "[runIde]   Status:    ${if (sandboxIsReused) "reused (existing sandbox with IDE caches/indices)" else "fresh (new sandbox - first-run indexing will be slower)"}",
        )
        logger.lifecycle("[runIde]   Plugins:   ${pluginNames.get()}")
        logger.lifecycle("[runIde]   IDE log:   ${ideaLog.absolutePath}")
        logger.lifecycle("[runIde]              tail -f ${ideaLog.absolutePath}")

        logger.lifecycle("[runIde] ─── mavenLocal XTC Artifacts ───")
        logger.lifecycle("[runIde]   ${xtcArtifacts.absolutePath}")
        if (xtcArtifacts.exists()) {
            xtcArtifacts.listFiles()?.sorted()?.forEach { artifact ->
                val versions = artifact.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                logger.lifecycle("[runIde]   ${artifact.name}: ${versions.joinToString(", ")}")
            }
        }

        logger.lifecycle("[runIde] ─── Reset Commands ───")
        logger.lifecycle("[runIde]   Nuke sandbox (keeps IDE download):  ./gradlew :lang:intellij-plugin:clean")
        logger.lifecycle("[runIde]   Nuke cached IDE + metadata:         rm -rf lang/.intellijPlatform/localPlatformArtifacts")

        logger.lifecycle("[runIde] LSP logs: ${lspLogDirectory.get()}/server-*/server.log (tailing new output to console)")
    }
}

@UntrackedTask(because = "Runtime log tail task manages background thread state and should never be state-tracked")
abstract class StartLogTailTask : DefaultTask() {
    // The directory may not exist until the first server starts; its live contents are not inputs.
    @get:Input
    abstract val logDirectory: Property<String>

    @get:Input
    abstract val threadName: Property<String>

    @get:Input
    abstract val linePrefix: Property<String>

    @TaskAction
    fun startTail() {
        val root = Path.of(logDirectory.get())
        val name = threadName.get()
        Thread.getAllStackTraces().keys.firstOrNull { it.name == name }?.interrupt()
        val prefix = linePrefix.get()
        val initial = ServerLogTails.poll(root, emptyMap(), skipExisting = true).positions
        thread(isDaemon = true, name = name) {
            // Owned exclusively by this tail thread; no compiler or IDE callbacks access the cursors.
            var positions = initial
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val batch = ServerLogTails.poll(root, positions)
                    positions = batch.positions
                    batch.lines.forEach { logger.lifecycle("$prefix$it") }
                    Thread.sleep(500)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (failure: Exception) {
                logger.warn("Ecstasy log tail stopped: ${failure.message}")
            }
        }
    }
}

@UntrackedTask(because = "Runtime log tail stop task manages background thread state and should never be state-tracked")
abstract class StopLogTailTask : DefaultTask() {
    @get:Input
    abstract val threadName: Property<String>

    @TaskAction
    fun stopTail() {
        Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName.get() }?.interrupt()
    }
}

/** Detached file cursors follow new sessions and rollover without keeping old file handles open. */
internal object ServerLogTails {
    private val sessionName = Regex("server-[0-9]+-[0-9]+")

    data class Position(val key: Any?, val offset: Long)

    data class Batch(val positions: Map<Path, Position>, val lines: List<String>)

    fun poll(root: Path, previous: Map<Path, Position>, skipExisting: Boolean = false): Batch {
        val paths = if (Files.isDirectory(root, NOFOLLOW_LINKS)) {
            Files.list(root).use { sessions ->
                sessions
                    .filter { Files.isDirectory(it, NOFOLLOW_LINKS) && sessionName.matches(it.fileName.toString()) }
                    .map { it.resolve("server.log") }
                    .toList()
            }
        } else emptyList()
        val rows = paths.mapNotNull { path ->
            try {
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (!attributes.isRegularFile) return@mapNotNull null
                val prior = previous[path]?.takeIf { it.key == attributes.fileKey() && it.offset <= attributes.size() }
                if (skipExisting) {
                    return@mapNotNull Triple(path, Position(attributes.fileKey(), attributes.size()), emptyList<String>())
                }
                val offset = prior?.offset ?: 0L
                Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                    channel.position(offset)
                    val bytes = ByteBuffer.allocate(minOf((channel.size() - offset).coerceAtLeast(0), 64 * 1024L).toInt())
                    while (bytes.hasRemaining() && channel.read(bytes) > 0) { /* bounded poll */ }
                    // Hold partial lines for the next poll; split oversized lines at a UTF-8 boundary.
                    val end = (0 until bytes.position()).lastOrNull { bytes[it] == '\n'.code.toByte() }?.plus(1)
                        ?: if (bytes.position() < 64 * 1024) {
                            0
                        } else {
                            (0 until bytes.position()).lastOrNull { bytes[it].toInt() and 0xC0 != 0x80 } ?: 0
                        }
                    val text = String(bytes.array(), 0, end, Charsets.UTF_8)
                    Triple(path, Position(attributes.fileKey(), offset + end), text.lineSequence().filter(String::isNotEmpty).toList())
                }
            } catch (_: NoSuchFileException) {
                null // A retired session or rolled file may disappear between discovery and reading.
            }
        }
        return Batch(rows.associate { it.first to it.second }, rows.flatMap { it.third })
    }
}
