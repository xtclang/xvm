import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileTime
import java.util.zip.GZIPInputStream

/**
 * Verifies that playbook cleanup reclaims disposable IDE caches without deleting reports, pinned artifacts,
 * unfinished runs or Git checkouts. Real directory and symlink fixtures protect the destructive cleanup
 * boundary.
 */
class TestReportRetentionTest {
    @TempDir lateinit var root: Path

    private fun run(name: String, time: Long, completed: Boolean = true): Path {
        val run = Files.createDirectories(root.resolve(name))
        Files.writeString(run.resolve("results.json"), "{\"failures\": 0}")
        Files.writeString(Files.createDirectory(run.resolve("workspace")).resolve("Sample.x"), "module Sample {}")
        if (completed) {
            Files.writeString(run.resolve(".completed"), "IDE exited")
            Files.setLastModifiedTime(run.resolve(".completed"), FileTime.fromMillis(time))
        }
        return run
    }

    @Test
    fun `retention preserves receipts recent payloads pins and unfinished runs`() {
        val old = run("run-old", 1)
        val pinned = run("run-pinned", 2)
        Files.createFile(pinned.resolve(".keep-artifacts"))
        val recent = run("run-recent", 3)
        val active = run("run-active", 4, completed = false)
        Files.writeString(old.resolve("client-trace.log"), "trace")
        TestReportRetention.prune(root, keep = 1)
        assertTrue(Files.exists(old.resolve("results.json")))
        assertFalse(Files.exists(old.resolve("workspace")))
        assertFalse(Files.exists(old.resolve("client-trace.log")))
        listOf(pinned, recent, active).forEach { assertTrue(Files.exists(it.resolve("workspace/Sample.x"))) }
    }

    @Test
    fun `completed IntelliJ sandboxes release caches but retain recent diagnostic logs`() {
        val old = run("run-old", 1)
        val recent = run("run-recent", 2)
        fun sandbox(run: Path): Path = root.resolve("out/ide-tests/tests/IU-1/XtcCompilerPlaybook-${run.fileName}")
        listOf(old, recent).forEach { run ->
            listOf("system", "config", "plugins", "temp", "log", "reports").forEach { name ->
                Files.writeString(Files.createDirectories(sandbox(run).resolve(name)).resolve("data"), "generated")
            }
        }
        TestReportRetention.prune(root, keep = 1)
        assertFalse(Files.exists(sandbox(old).resolve("log")))
        assertFalse(Files.exists(sandbox(recent).resolve("system")))
        assertTrue(Files.exists(sandbox(recent).resolve("log/data")))
        assertTrue(Files.exists(sandbox(recent).resolve("reports/data")))
    }

    @Test
    fun `cleanup never follows payload symlinks or removes Git worktrees`() {
        val old = run("run-old", 1)
        run("run-new", 2)
        val outside = Files.createDirectories(root.resolve("source"))
        Files.writeString(outside.resolve("precious.x"), "module Precious {}")
        Files.createSymbolicLink(old.resolve("workspace/external"), outside)
        val worktree = Files.createDirectories(old.resolve("unexpected-worktree"))
        Files.writeString(worktree.resolve(".git"), "gitdir: preserve")
        TestReportRetention.prune(root, keep = 1)
        TestReportRetention.prune(root, keep = 1)
        assertTrue(Files.exists(outside.resolve("precious.x")))
        assertTrue(Files.exists(worktree.resolve(".git")))
        assertFalse(Files.exists(old.resolve("workspace")))
    }

    /** A completed run of [mode] whose `results.json` is [results], with a server trace, a screenshot and logs. */
    private fun traced(name: String, time: Long, mode: String, results: String): Path {
        val run = run(name, time)
        Files.writeString(run.resolve("results.json"), "{\"mode\": \"$mode\", $results}")
        Files.writeString(Files.createDirectories(run.resolve("server-trace")).resolve("lsp-trace-1-a.jsonl"), TRACE)
        Files.writeString(Files.createDirectories(run.resolve("frames")).resolve("1.png"), "frame")
        Files.writeString(Files.createDirectories(run.resolve("server-logs")).resolve("server.log"), "log")
        Files.writeString(Files.createDirectories(run.resolve("ide-log")).resolve("idea.log"), "log")
        return run
    }

    private fun trace(run: Path): Path = run.resolve("server-trace/lsp-trace-1-a.jsonl")

    private fun gzipped(file: Path): String =
        GZIPInputStream(Files.newInputStream(file.resolveSibling("${file.fileName}.gz"))).use { String(it.readAllBytes()) }

    @Test
    fun `completed runs' server traces and large client traces are compressed, and a run in progress is left alone`() {
        val done = traced("run-done", 1, "FEATURES", "\"counts\": {\"passed\": 1}")
        val large = "x".repeat(TestReportRetention.CLIENT_TRACE_COMPRESSED_FROM.toInt())
        Files.writeString(done.resolve("client-trace-X71.log"), large)
        Files.writeString(done.resolve("client-trace-X72.log"), "small")
        val active = run("run-active", 2, completed = false)
        Files.writeString(Files.createDirectories(active.resolve("server-trace")).resolve("lsp-trace-2-a.jsonl"), TRACE)
        val pruned = TestReportRetention.prune(root, keep = 5)
        assertEquals(TRACE, gzipped(trace(done)))
        assertFalse(Files.exists(trace(done)))
        assertEquals(large, gzipped(done.resolve("client-trace-X71.log")))
        assertEquals("small", Files.readString(done.resolve("client-trace-X72.log")))
        assertEquals(TRACE, Files.readString(active.resolve("server-trace/lsp-trace-2-a.jsonl")))
        assertFalse(Files.exists(active.resolve("server-trace/lsp-trace-2-a.jsonl.gz")))
        assertEquals(TestReportRetention.Pruned(runs = 0, compressed = 2, dropped = 0), pruned)
        // a second prune finds nothing more to do
        assertEquals(TestReportRetention.Pruned(), TestReportRetention.prune(root, keep = 5))
        assertEquals(TRACE, gzipped(trace(done)))
    }

    @Test
    fun `of the kept runs, those with a failure and the newest passing run of each mode keep their traces`() {
        val passed = "\"counts\": {\"passed\": 3}, \"cases\": [{\"status\": \"passed\"}], \"ideFailures\": []"
        val olderPassing = traced("run-older-passing", 1, "FEATURES", passed)
        val failedCase = traced("run-failed-case", 2, "FEATURES", "\"counts\": {\"failed\": 1}, \"cases\": [{\"status\": \"failed\"}]")
        val ideFailure = traced("run-ide-failure", 3, "FEATURES", "\"counts\": {\"passed\": 3}, \"ideFailures\": [\"boom\"]")
        val pinned = traced("run-pinned", 4, "FEATURES", passed)
        Files.createFile(pinned.resolve(".keep-artifacts"))
        val newestPassing = traced("run-newest-passing", 5, "FEATURES", passed)
        val olderTour = traced("run-tour-older", 6, "TOUR", "\"failed\": []")
        val failedTour = traced("run-tour-failed", 7, "TOUR", "\"failed\": [\"Diagnostics: no popup\"]")
        val escaped = traced("run-tour-escaped", 8, "TOUR", "\"failed\": [], \"failure\": \"IDE crashed\"")
        val newestTour = traced("run-tour-newest", 9, "TOUR", "\"failed\": []")
        val unreadable = traced("run-unreadable", 10, "FEATURES", passed)
        Files.writeString(unreadable.resolve("results.json"), "{\"counts\": ")
        val pruned = TestReportRetention.prune(root, keep = 10)
        listOf(olderPassing, olderTour).forEach { run ->
            assertFalse(Files.exists(trace(run)))
            assertFalse(Files.exists(trace(run).resolveSibling("lsp-trace-1-a.jsonl.gz")))
            listOf("results.json", "frames/1.png", "server-logs/server.log", "ide-log/idea.log").forEach {
                assertTrue(Files.exists(run.resolve(it)), "$run keeps $it")
            }
        }
        listOf(failedCase, ideFailure, newestPassing, failedTour, escaped, newestTour, unreadable).forEach {
            assertEquals(TRACE, gzipped(trace(it)), "$it keeps its trace")
        }
        // a pinned run is left as it is
        assertEquals(TRACE, Files.readString(trace(pinned)))
        assertEquals(TestReportRetention.Pruned(runs = 0, compressed = 7, dropped = 2), pruned)
    }

    /** Whether [run] has kept its payloads: its workspace, server trace, screenshot and logs, the trace compressed. */
    private fun whole(run: Path): Boolean =
        listOf("workspace/Sample.x", "frames/1.png", "server-logs/server.log", "ide-log/idea.log").all {
            Files.exists(run.resolve(it))
        } && gzipped(trace(run)) == TRACE

    /** Whether [run] has been pruned as an old run: its results kept, its payloads and traces gone. */
    private fun onlyResults(run: Path): Boolean =
        Files.exists(run.resolve("results.json")) &&
            listOf("workspace", "server-trace", "frames", "server-logs", "ide-log").none { Files.exists(run.resolve(it)) }

    @Test
    fun `the newest failed runs keep their payloads and traces after newer passing runs, and older failed runs are pruned`() {
        val failed = "\"counts\": {\"failed\": 1}, \"cases\": [{\"status\": \"failed\"}]"
        val passed = "\"counts\": {\"passed\": 3}, \"cases\": [{\"status\": \"passed\"}]"
        val oldestFailed = traced("run-failed-1", 1, "FEATURES", failed)
        val pinned = traced("run-failed-2", 2, "FEATURES", failed)
        Files.createFile(pinned.resolve(".keep-artifacts"))
        val recentFailed = listOf(3L, 4L, 5L).map { traced("run-failed-$it", it, "FEATURES", failed) }
        val passing = listOf(6L, 7L, 8L).map { traced("run-passed-$it", it, "FEATURES", passed) }
        val pruned = TestReportRetention.prune(root, keep = 2, keepFailed = 3)
        // the three newest failures keep everything, however many passing runs came after them
        recentFailed.forEach { assertTrue(whole(it), "$it keeps its payloads and traces") }
        // an older failure, past the newest three, keeps its results as an old passing run does
        assertTrue(onlyResults(oldestFailed), "$oldestFailed keeps only its results")
        assertTrue(Files.exists(pinned.resolve("workspace/Sample.x")))
        assertEquals(TRACE, Files.readString(trace(pinned)))
        assertTrue(onlyResults(passing[0]), "${passing[0]} keeps only its results")
        assertEquals(TestReportRetention.Pruned(runs = 2, compressed = 4, dropped = 1), pruned)
    }

    @Test
    fun `passing runs keep their payloads only among the newest runs, and their traces only as their mode's newest`() {
        val passed = "\"counts\": {\"passed\": 3}, \"cases\": [{\"status\": \"passed\"}]"
        val failed = traced("run-failed", 1, "FEATURES", "\"counts\": {\"failed\": 1}")
        val older = traced("run-passed-older", 2, "FEATURES", passed)
        val previous = traced("run-passed-previous", 3, "FEATURES", passed)
        val newest = traced("run-passed-newest", 4, "FEATURES", passed)
        val pruned = TestReportRetention.prune(root, keep = 2)
        // past the newest two, a passing run keeps only its results, while the failure before it keeps everything
        assertTrue(onlyResults(older), "$older keeps only its results")
        assertTrue(whole(failed), "$failed keeps its payloads and traces")
        // of the newest two, the newest passing run keeps its trace, the baseline for a failure; the other drops it
        assertTrue(whole(newest), "$newest keeps its payloads and traces")
        assertFalse(Files.exists(trace(previous)))
        assertFalse(Files.exists(trace(previous).resolveSibling("lsp-trace-1-a.jsonl.gz")))
        assertTrue(Files.exists(previous.resolve("workspace/Sample.x")))
        assertTrue(Files.exists(previous.resolve("frames/1.png")))
        assertEquals(TestReportRetention.Pruned(runs = 1, compressed = 2, dropped = 1), pruned)
    }

    @Test
    fun `pinned runs take none of the newest runs' or the newest failures' places`() {
        val passed = "\"counts\": {\"passed\": 3}, \"cases\": [{\"status\": \"passed\"}]"
        val failed = "\"counts\": {\"failed\": 1}, \"cases\": [{\"status\": \"failed\"}]"
        val olderFailed = traced("run-failed-1", 1, "FEATURES", failed)
        val newerFailed = traced("run-failed-2", 2, "FEATURES", failed)
        val pinnedFailed = traced("run-failed-pinned", 3, "FEATURES", failed)
        val newestPassing = traced("run-passed", 4, "FEATURES", passed)
        val pinnedPassing = traced("run-passed-pinned", 5, "FEATURES", passed)
        listOf(pinnedFailed, pinnedPassing).forEach { Files.createFile(it.resolve(".keep-artifacts")) }
        val pruned = TestReportRetention.prune(root, keep = 1, keepFailed = 1)
        // the newest run and the newest failure are the newest of those not pinned
        assertTrue(whole(newestPassing), "$newestPassing keeps its payloads and traces")
        assertTrue(whole(newerFailed), "$newerFailed keeps its payloads and traces")
        assertTrue(onlyResults(olderFailed), "$olderFailed keeps only its results")
        listOf(pinnedFailed, pinnedPassing).forEach { assertEquals(TRACE, Files.readString(trace(it))) }
        assertEquals(TestReportRetention.Pruned(runs = 1, compressed = 2, dropped = 0), pruned)
    }

    @Test
    fun `a VS Code playbook run with failures, or with a selected case that did not pass, is kept as a failure`() {
        val counted = "\"failures\": 1, \"cases\": [{\"id\": \"X1\", \"status\": \"passed\"}]"
        val notRun = "\"failures\": 0, \"selection\": {\"mode\": \"focused\", \"ids\": [\"X1\", \"X2\"]}, \"cases\": " +
            "[{\"id\": \"X1\", \"status\": \"passed\"}, {\"id\": \"X2\", \"status\": \"not-run\"}, " +
            "{\"id\": \"X3\", \"status\": \"not-selected\"}]"
        val passed = "\"failures\": 0, \"selection\": {\"mode\": \"focused\", \"ids\": [\"X1\"]}, \"cases\": " +
            "[{\"id\": \"X1\", \"status\": \"passed\"}, {\"id\": \"X2\", \"status\": \"not-selected\"}]"
        // mocha counted a failure in a hook, which no case's status names
        val failures = traced("run-failures", 1, "FEATURES", counted)
        // its run failed only at its last check, that no selected case was skipped
        val skipped = traced("run-skipped", 2, "FEATURES", notRun)
        val passing = traced("run-passed", 3, "FEATURES", passed)
        val pruned = TestReportRetention.prune(root, keep = 1, keepFailed = 2)
        listOf(failures, skipped, passing).forEach { assertTrue(whole(it), "$it keeps its payloads and traces") }
        assertEquals(TestReportRetention.Pruned(runs = 0, compressed = 3, dropped = 0), pruned)
    }

    @Test
    fun `a VS Code playbook run whose selected cases passed but for a known host failure is kept as a passing run`() {
        val known = "\"failures\": 0, \"selection\": {\"mode\": \"focused\", \"ids\": [\"X1\", \"X218\"]}, \"cases\": " +
            "[{\"id\": \"X1\", \"status\": \"passed\"}, {\"id\": \"X218\", \"status\": \"known-host-failure\", \"knownHost\": \"UP23\"}]"
        val passed = "\"failures\": 0, \"selection\": {\"mode\": \"focused\", \"ids\": [\"X1\"]}, \"cases\": " +
            "[{\"id\": \"X1\", \"status\": \"passed\"}]"
        val older = traced("run-known", 1, "FEATURES", known)
        traced("run-passed", 2, "FEATURES", passed)
        TestReportRetention.prune(root, keep = 1)
        // past the newest run, it keeps only its results, as a passing run does, and a failed one would not
        assertTrue(onlyResults(older), "$older keeps only its results")
    }

    @Test
    fun `a prune that cannot take the lock leaves every run alone`() {
        val done = traced("run-done", 1, "FEATURES", "\"counts\": {\"passed\": 1}")
        FileChannel.open(root.resolve(".retention.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertEquals(TestReportRetention.Pruned(), TestReportRetention.prune(root, keep = 5))
                assertEquals(TRACE, Files.readString(trace(done)))
            }
        }
        TestReportRetention.prune(root, keep = 5)
        assertEquals(TRACE, gzipped(trace(done)))
    }

    private companion object {
        const val TRACE = "{\"kind\":\"lsp-request\",\"event\":\"start\"}\n{\"kind\":\"lsp-request\",\"event\":\"end\"}\n"
    }
}
