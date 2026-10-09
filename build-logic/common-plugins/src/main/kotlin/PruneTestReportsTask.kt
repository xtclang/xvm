import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** Prunes disposable payloads only after a runner confirms that its IDE has exited. */
@DisableCachingByDefault(because = "Retention acts on completed test runs, not reproducible task outputs")
abstract class PruneTestReportsTask : DefaultTask() {
    @get:Internal
    abstract val reportsDirectory: DirectoryProperty

    @get:Input
    abstract val retainedRuns: Property<Int>

    init {
        retainedRuns.convention(5)
    }

    @TaskAction
    fun prune() {
        val count = TestReportRetention.prune(reportsDirectory.get().asFile.toPath(), retainedRuns.get())
        logger.lifecycle("Test report retention: pruned payloads from {} old runs; summaries retained", count)
    }
}

internal object TestReportRetention {
    fun prune(root: Path, keep: Int = 5): Int {
        require(keep >= 1) { "Keep at least one complete run" }
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return 0
        return FileChannel.open(root.resolve(".retention.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            lock?.use { pruneClosed(root, keep) } ?: 0
        }
    }

    private fun pruneClosed(root: Path, keep: Int): Int {
        val runs = children(root)
            .filter { it.fileName.toString().startsWith("run-") && Files.isDirectory(it, NOFOLLOW_LINKS) }
            .filter { Files.isRegularFile(it.resolve(".completed"), NOFOLLOW_LINKS) }
            .sortedByDescending { Files.getLastModifiedTime(it.resolve(".completed")) }
        val old = runs.drop(keep).filterNot { Files.exists(it.resolve(".keep-artifacts"), NOFOLLOW_LINKS) }.toSet()
        runs.filterNot { Files.exists(it.resolve(".keep-artifacts"), NOFOLLOW_LINKS) }.forEach { run ->
            // Starter stores each sandbox separately from the run's result files.
            val sandboxes = children(root.resolve("out/ide-tests/tests"))
                .filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
                .map { it.resolve("XtcCompilerPlaybook-${run.fileName}") }
                .filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
            sandboxes.forEach { sandbox ->
                val payloads = if (run in old) children(sandbox)
                    else listOf("system", "plugins", "config", "temp").map(sandbox::resolve)
                payloads.forEach(::removePayload)
            }
            if (run in old) {
                children(run).filter {
                    Files.isDirectory(it, NOFOLLOW_LINKS) || Files.isSymbolicLink(it) || it.fileName.toString().endsWith(".log")
                }.forEach(::removePayload)
            }
        }
        return old.size
    }

    private fun children(path: Path): List<Path> =
        if (Files.isDirectory(path, NOFOLLOW_LINKS)) Files.list(path).use { it.toList() } else emptyList()

    private fun removePayload(path: Path) {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return
        // walk does not follow symlinks; never touch a source worktree accidentally placed here.
        val paths = Files.walk(path).use { it.toList() }
        if (paths.any { it.fileName.toString() == ".git" }) return
        paths.sortedDescending().forEach(Files::deleteIfExists)
    }
}
