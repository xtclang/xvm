package org.xvm.lsp.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.testing.awaitUntil
import org.xvm.testing.awaited
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Checks the cleanup of the directories a server process owns in the temporary directory, such as its read-only copies
 * of library sources: it deletes its own when its JVM exits, and those of servers that were killed. A running server
 * is one that holds its directory's lock, which a [SessionOwner] in a process of its own does here.
 */
class ProcessDirectoriesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a directory is deleted with the read-only files in it`() {
        val copies = copies("xtc-library-sources-1")
        assertThat(ProcessDirectories.delete(copies)).isTrue()
        assertThat(copies).doesNotExist()
    }

    @Test
    fun `a path is held by a directory of a kind only where the directory is named as a server names it`() {
        val temporary = File(System.getProperty("java.io.tmpdir")).canonicalFile.toPath()
        val made = temporary.resolve("xtc-library-sources-5366563676160639398")
        assertThat(ProcessDirectories.holder(made.resolve("ecstasy/collections/Array.x"), PREFIX)).isEqualTo(made)
        // a user's folder whose name only starts with the prefix, or is the prefix alone, is not one
        assertThat(ProcessDirectories.holder(temporary.resolve("xtc-library-sources-myapp/App.x"), PREFIX)).isNull()
        assertThat(ProcessDirectories.holder(temporary.resolve("xtc-library-sources-/App.x"), PREFIX)).isNull()
        // nor is the directory itself, or a folder of that name below another folder
        assertThat(ProcessDirectories.holder(made, PREFIX)).isNull()
        assertThat(ProcessDirectories.holder(directory.resolve("xtc-library-sources-1/App.x"), PREFIX)).isNull()
    }

    @Test
    fun `a directory stays held for other processes when its process makes another of its kind`() {
        // in the temporary directory, where a server makes its own, under a prefix of this test's
        val first = ProcessDirectories.create(HELD)
        try {
            assertThat(lockable(first)).describedAs("Another process takes the first directory's lock").isFalse()
            // the second's cleanup asks about the directories of its kind, the first among them: a look at the first's lock
            //  that closed its channel to the lock file would release this process's lock on it, and a server that makes
            //  a directory of the kind next would delete it, as test JVMs running beside each other did
            val second = ProcessDirectories.create(HELD)
            try {
                assertThat(lockable(first)).describedAs("Another process takes the first directory's lock").isFalse()
                assertThat(lockable(second)).describedAs("Another process takes the second directory's lock").isFalse()
            } finally {
                ProcessDirectories.delete(second)
            }
        } finally {
            ProcessDirectories.delete(first)
        }
    }

    @Test
    fun `two threads making directories of a kind at once delete none of them, and every lock stays held`() {
        // the first, alone, loads the code that the threads run, so that a thread that waits in it waits for the guard
        val made = ConcurrentLinkedQueue(listOf(ProcessDirectories.create(CONCURRENT)))
        val failures = ConcurrentLinkedQueue<Throwable>()
        val makers =
            List(2) {
                Thread.ofPlatform().unstarted {
                    runCatching { repeat(ROUNDS) { made.add(ProcessDirectories.create(CONCURRENT)) } }.onFailure(failures::add)
                }
            }
        // another server holds the kind's guard, so that the two threads come to it at once
        val guard = owner("guard")
        try {
            guard.outputWriter().apply {
                write("${Path.of(System.getProperty("java.io.tmpdir")).resolve(".${CONCURRENT}lock")}\n")
                flush()
            }
            assertThat(guard.inputReader().readLine()).describedAs("The other process holds the kind's guard").isEqualTo("true")
            makers.forEach(Thread::start)
            // the thread that takes the kind's monitor waits for the guard, and the other for the monitor; without the
            //  monitor, the other's attempt at the guard failed, as the first's lock was this process's, and it made its
            //  directories unguarded, which the closing of its channel to the guard file had released
            awaitUntil("a thread waits at the kind's guard for the other, or ends") {
                makers.any { !it.isAlive || (it.state == Thread.State.BLOCKED && it.stackTrace.firstOrNull()?.methodName == "guard") }
            }
            assertThat(made).describedAs("Directories made while another process holds the kind's guard").hasSize(1)
            guard.outputStream.close()
            makers.forEach { it.awaited() }
            assertThat(failures).isEmpty()
            assertThat(made).hasSize(1 + 2 * ROUNDS).allMatch { Files.isDirectory(it) && Files.isRegularFile(SessionLock.beside(it)) }
            assertThat(lockable(made.toList())).describedAs("Another process takes a directory's lock").containsOnly(false)
        } finally {
            guard.outputStream.close()
            if (!guard.awaited()) guard.destroyForcibly()
            makers.filter { it.state != Thread.State.NEW }.forEach { it.awaited() }
            made.forEach(ProcessDirectories::delete)
        }
    }

    /** A [SessionOwner] in a process of its own, in [mode], until its input ends. */
    private fun owner(mode: String): Process =
        ProcessBuilder(
            ProcessHandle
                .current()
                .info()
                .command()
                .orElseThrow(),
            "-cp",
            System.getProperty("java.class.path"),
            SessionOwner::class.java.name,
            mode,
        ).redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    /** Whether a [SessionOwner] in a process of its own can take [directory]'s lock beside it, as a server that deletes it does. */
    private fun lockable(directory: Path): Boolean = lockable(listOf(directory)).single()

    /** Whether a [SessionOwner] in a process of its own can take each of [directories]' locks beside them. */
    private fun lockable(directories: List<Path>): List<Boolean> {
        val owner = owner("processes")
        try {
            owner.outputWriter().apply {
                directories.forEach { write("$it\n") }
                write("\n")
                flush()
            }
            return directories.map { owner.inputReader().readLine() == "true" }
        } finally {
            owner.outputStream.close()
            if (!owner.awaited()) owner.destroyForcibly()
        }
    }

    @Test
    fun `a server deletes the directories of a server that was killed, not those of a running or an older server`() {
        val own = copies("xtc-library-sources-1")
        val lock = SessionLock.hold(own, SessionLock.beside(own))
        // the system released the killed server's lock, and left its lock file
        val killed = copies("xtc-library-sources-2").also { Files.createFile(SessionLock.beside(it)) }
        // an older server took no lock beside it
        val older = copies("xtc-library-sources-3").also { Files.createFile(it.resolve(SessionLock.NAME)) }
        // which reads every file in its directory once it holds it, as a compile fingerprinting resources does: had
        // the lock been in the directory, that would have released it
        val running = copies("xtc-library-sources-4")
        val owner =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                SessionOwner::class.java.name,
                "process",
            ).redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        try {
            owner.outputWriter().apply {
                write("$running\n")
                flush()
            }
            assertThat(owner.inputReader().readLine()).describedAs("The other process holds its directory").isEqualTo("true")

            ProcessDirectories.deleteAbandoned(own, "xtc-library-sources-")

            assertThat(killed).doesNotExist()
            assertThat(SessionLock.beside(killed)).doesNotExist()
            assertThat(listOf(own, older, running)).allMatch { Files.isRegularFile(it.resolve(COLLECTION)) }
        } finally {
            owner.outputStream.close()
            if (!owner.awaited()) owner.destroyForcibly()
            lock?.channel()?.close()
        }
    }

    /** A directory of copies, as a server writes it, with read-only copies. */
    private fun copies(name: String): Path =
        Files.createDirectories(directory.resolve(name)).also { copies ->
            listOf("ecstasy.x", COLLECTION).forEach { path ->
                val file = copies.resolve(path)
                Files.createDirectories(file.parent)
                Files.writeString(file, "module ecstasy {}\n")
                check(file.toFile().setReadOnly())
            }
        }

    private companion object {
        const val COLLECTION = "ecstasy/collections/Collection.x"
        const val PREFIX = "xtc-library-sources-"

        /** The kind of this test's own directories in the temporary directory, apart from the servers'. */
        const val HELD = "xtc-held-test-"

        /** The kind of the directories that two threads make at once, apart from [HELD]'s. */
        const val CONCURRENT = "xtc-concurrent-test-"

        /** How many directories each of the two threads makes. */
        const val ROUNDS = 20
    }
}
