import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class TestReportRetentionTest {
    @TempDir lateinit var root: Path

    private fun run(name: String, time: Long, completed: Boolean = true): Path {
        val run = Files.createDirectories(root.resolve(name))
        Files.writeString(run.resolve("results.json"), "{\"failures\": 1}")
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
}
