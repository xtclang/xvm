import groovy.json.JsonSlurper
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
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.zip.GZIPOutputStream

/** Prunes disposable payloads only after a runner confirms that its IDE has exited. */
@DisableCachingByDefault(because = "Retention acts on completed test runs, not reproducible task outputs")
abstract class PruneTestReportsTask : DefaultTask() {
    @get:Internal
    abstract val reportsDirectory: DirectoryProperty

    @get:Input
    abstract val retainedRuns: Property<Int>

    /** How many of the newest failed runs keep their payloads and traces, also when they are not among [retainedRuns]. */
    @get:Input
    abstract val retainedFailedRuns: Property<Int>

    init {
        retainedRuns.convention(5)
        retainedFailedRuns.convention(10)
    }

    @TaskAction
    fun prune() {
        val pruned =
            TestReportRetention.prune(reportsDirectory.get().asFile.toPath(), retainedRuns.get(), retainedFailedRuns.get())
        logger.lifecycle(
            "Test report retention: pruned payloads from {} old runs, compressed {} traces, dropped the traces of {} " +
                "passing runs; summaries retained",
            pruned.runs, pruned.compressed, pruned.dropped
        )
    }
}

/**
 * Prunes disposable IDE payloads from completed playbook runs while retaining their reports. Cleanup is
 * serialized by a file lock, honors explicit artifact retention, and refuses to traverse symlinks or delete a
 * checkout.
 *
 * Traces, the language server's (`lsp-trace-*.jsonl`) and the client's (`client-trace-*.log`), are what a run
 * writes most of. A completed run's server traces, and its client traces of [CLIENT_TRACE_COMPRESSED_FROM] bytes or
 * more, are compressed with gzip. The [keep] newest completed runs keep their payloads, and so do the [keepFailed]
 * newest runs with a failure, with their traces, so that a failure's evidence outlives the passing runs after it; the
 * other runs, passed or failed, keep only their results. Of the passing runs that keep their payloads, the newest of
 * each mode keeps its traces, the baseline to compare a failure with; the others drop theirs, and keep their results,
 * screenshots and logs. Whether a run passed is read from its `results.json`; a run whose results do not say counts as
 * failed. A run with `.keep-artifacts` is never touched, and is not one of the [keep] or [keepFailed] newest.
 */
internal object TestReportRetention {
    /** Client traces from this size on are compressed; most are a few kilobytes. */
    const val CLIENT_TRACE_COMPRESSED_FROM = 1L shl 20

    /** The statuses of a selected case of VS Code's playbook that leave its run green: a pass, and a known host bug's failure. */
    private val PASSING = setOf("passed", "known-host-failure")

    private val serverTrace = Regex("lsp-trace-.+\\.jsonl")
    private val clientTrace = Regex("client-trace-.+\\.log")

    /** What a prune did: the old runs it pruned, the traces it compressed, the passing runs whose traces it dropped. */
    data class Pruned(val runs: Int = 0, val compressed: Int = 0, val dropped: Int = 0)

    fun prune(root: Path, keep: Int = 5, keepFailed: Int = 10): Pruned {
        require(keep >= 1) { "Keep at least one complete run" }
        require(keepFailed >= 0) { "Keep no negative number of failed runs" }
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return Pruned()
        return FileChannel.open(root.resolve(".retention.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            lock?.use { pruneClosed(root, keep, keepFailed) } ?: Pruned()
        }
    }

    private fun pruneClosed(root: Path, keep: Int, keepFailed: Int): Pruned {
        val runs = children(root)
            .filter { it.fileName.toString().startsWith("run-") && Files.isDirectory(it, NOFOLLOW_LINKS) }
            .filter { Files.isRegularFile(it.resolve(".completed"), NOFOLLOW_LINKS) }
            .sortedByDescending { Files.getLastModifiedTime(it.resolve(".completed")) }
        // a pinned run is left as it is, and takes none of the places below
        val unpinned = runs.filterNot { Files.exists(it.resolve(".keep-artifacts"), NOFOLLOW_LINKS) }
        val passing = unpinned.filter(::passed).toSet()
        // the newest runs, and the newest failures, which keep their evidence however many runs pass after them
        val retained = (unpinned.take(keep) + unpinned.filterNot { it in passing }.take(keepFailed)).toSet()
        val old = unpinned.filterNot { it in retained }.toSet()
        val kept = unpinned.filter { it in retained }
        val keptPassing = kept.filter { it in passing }
        val baselines = keptPassing.groupBy(::mode).values.map { it.first() }.toSet()
        val dropped = keptPassing.filterNot { it in baselines }.filter(::dropTraces)
        val compressed = (kept - dropped.toSet()).sumOf(::compressTraces)
        unpinned.forEach { run ->
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
        return Pruned(old.size, compressed, dropped.size)
    }

    /**
     * Whether [run]'s `results.json` says it passed: it names no failed case, step or check, no IDE failure and no
     * error, counts no failure, and every case that VS Code's playbook selected passed, or failed with a known host
     * bug's symptom, which leaves the run green, as its run asserts after it writes the file, in the formats of
     * IntelliJ's and VS Code's playbooks, tours and probes.
     */
    private fun passed(run: Path): Boolean {
        val json = results(run) ?: return false
        val counts = json["counts"] as? Map<*, *>
        val cases = (json["cases"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val selected = ((json["selection"] as? Map<*, *>)?.get("ids") as? List<*>).orEmpty().toSet()
        return json["status"] != "failed" &&
            json["error"] == null &&
            json["failure"] == null &&
            (json["failed"] as? List<*>).isNullOrEmpty() &&
            ((counts?.get("failed") as? Number)?.toInt() ?: 0) == 0 &&
            ((json["failures"] as? Number)?.toInt() ?: 0) == 0 &&
            cases.none { it["status"] == "failed" || (it["id"] in selected && it["status"] !in PASSING) } &&
            (json["ideFailures"] as? List<*>).isNullOrEmpty() &&
            (json["errors"] as? List<*>).isNullOrEmpty()
    }

    /** [run]'s mode, as its `results.json` names it, such as a features playbook's or a tour's; null if it names none. */
    private fun mode(run: Path): Any? = results(run)?.get("mode")

    /** [run]'s `results.json`; null if there is none, or it is no JSON object. */
    private fun results(run: Path): Map<*, *>? {
        val results = run.resolve("results.json")
        if (!Files.isRegularFile(results, NOFOLLOW_LINKS)) return null
        return runCatching { JsonSlurper().parse(results.toFile()) }.getOrNull() as? Map<*, *>
    }

    /** [run]'s traces, compressed or not: in the run's directory and the directories in it, such as `server-trace`. */
    private fun traces(run: Path): List<Path> =
        Files.walk(run, 2).use { paths ->
            paths
                .filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }
                .filter { trace(it.fileName.toString().removeSuffix(".partial").removeSuffix(".gz")) }
                .toList()
        }

    private fun trace(name: String): Boolean = serverTrace.matches(name) || clientTrace.matches(name)

    /** Deletes [run]'s traces; answers whether it had any. */
    private fun dropTraces(run: Path): Boolean = traces(run).onEach(Files::delete).isNotEmpty()

    /** Compresses [run]'s server traces, and its client traces that are large; answers how many. */
    private fun compressTraces(run: Path): Int {
        val compressible = traces(run).filter {
            val name = it.fileName.toString()
            serverTrace.matches(name) || (clientTrace.matches(name) && Files.size(it) >= CLIENT_TRACE_COMPRESSED_FROM)
        }
        compressible.forEach(::gzip)
        return compressible.size
    }

    /** Replaces [file] with `<file>.gz`, written beside it first, so that an interrupted prune loses nothing. */
    private fun gzip(file: Path) {
        val partial = file.resolveSibling("${file.fileName}.gz.partial")
        GZIPOutputStream(Files.newOutputStream(partial)).use { Files.copy(file, it) }
        Files.setLastModifiedTime(partial, Files.getLastModifiedTime(file))
        Files.move(partial, file.resolveSibling("${file.fileName}.gz"), REPLACE_EXISTING, ATOMIC_MOVE)
        Files.delete(file)
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
