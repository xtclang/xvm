package org.xvm.lsp.util

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.awaited
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * Exercises session pruning and bounded support archives using real temporary files. Live processes, foreign
 * entries and symlinks must remain untouched, and invalid retention limits must fail before cleanup. A live
 * server is one that holds its session's lock, which a [SessionOwner] in a process of its own does here.
 */
class ServerLogsTest {
    @TempDir lateinit var root: Path

    @Test fun `pruning keeps live processes and foreign files while bounding closed sessions and age`() {
        val now = Instant.parse("2026-10-06T12:00:00Z")

        fun session(
            id: Int,
            ageDays: Long,
            file: String = "server.log",
        ): Path {
            val path = Files.createDirectory(root.resolve("server-$id-1"))
            val time = FileTime.from(now.minusSeconds(ageDays * 86400))
            Files.writeString(path.resolve(file), "retained log")
            Files.setLastModifiedTime(path.resolve(file), time)
            Files.setLastModifiedTime(path, time)
            return path
        }
        val live = session(1, 60)
        val newest = session(2, 0)
        val older = session(3, 1)
        val expired = session(4, 30)
        val foreign = session(5, 60, "unrelated.txt")
        val target = Files.createDirectory(root.resolve("unrelated"))
        Files.createSymbolicLink(root.resolve("server-6-1"), target)
        assertThat(ServerLogs.prune(root, LogRetention(retainedSessions = 1), now) { pid, _ -> pid == 1L }).isEqualTo(2)
        assertThat(live).exists()
        assertThat(newest).exists()
        assertThat(foreign).exists()
        assertThat(target).exists()
        assertThat(older).doesNotExist()
        assertThat(expired).doesNotExist()
    }

    @Test fun `pruning keeps a live server's session whatever start time its name has`() {
        val now = Instant.parse("2026-10-06T12:00:00Z")
        Owner("hold").use { owner ->
            // the owner's JVM named its session from the start it computed for itself; this JVM, after a step of
            // the clock, computes another for the owner's PID, as a playbook run's two JVMs did, 5,328 s apart
            val process = owner.process
            val started = process.info().startInstant().orElseThrow()
            val session = Files.createDirectory(root.resolve("server-${process.pid()}-${started.toEpochMilli() + 5_328_000}"))
            Files.writeString(session.resolve("server.log"), "live log")
            // the owner answers once it holds the session
            owner.reply(session.toString())
            age(session, now.minusSeconds(60 * 86400))
            assertThat(ServerLogs.prune(root, LogRetention(historyDays = 1), now)).isZero()
            assertThat(session.resolve("server.log")).exists()
        }
    }

    @Test fun `pruning removes a session whose lock no process holds, whatever its name's PID and start time say`() {
        val now = Instant.parse("2026-10-06T12:00:00Z")
        val session = Files.createDirectory(root.resolve("server-7-1"))
        Files.writeString(session.resolve("server.log"), "closed log")
        // a server that ended left its lock file, and the system released its lock
        Files.createFile(session.resolve(SessionLock.NAME))
        age(session, now.minusSeconds(60 * 86400))
        // the PID is live with that start time, as a reused one, or one another JVM computes, can be
        assertThat(ServerLogs.prune(root, LogRetention(historyDays = 1), now) { _, _ -> true }).isEqualTo(1)
        assertThat(session).doesNotExist()
    }

    @Test fun `a directory that servers share is held while any of them runs, and one nobody holds is unheld`() {
        val own = Files.createDirectory(root.resolve("xtc-lsp-xdk-1"))
        val shared = Files.createDirectory(root.resolve("xtc-lsp-xdk-2"))
        // a server of another build ended, and the system released its lock
        val ended = Files.createDirectory(root.resolve("xtc-lsp-xdk-3")).also { Files.createFile(SessionLock.beside(it)) }
        // a server wrote it before servers took the lock, so it is never unheld
        Files.createDirectory(root.resolve("xtc-lsp-xdk-4"))
        Owner("share").use { first ->
            Owner("share").use { second ->
                assertThat(first.reply(shared.toString())).isEqualTo("true")
                assertThat(second.reply(shared.toString())).describedAs("Servers of one build share it").isEqualTo("true")
                assertThat(SessionLock.unheld(own, "xtc-lsp-xdk-*")).containsExactly(ended)
            }
            assertThat(SessionLock.unheld(own, "xtc-lsp-xdk-*")).describedAs("One server still holds it").containsExactly(ended)
        }
        assertThat(SessionLock.unheld(own, "xtc-lsp-xdk-*")).containsExactlyInAnyOrder(shared, ended)
    }

    @Test fun `a server's pruning keeps its own session locked`() {
        Owner("server").use { owner ->
            val session = Path.of(owner.reply())
            assertThat(session.parent).isEqualTo(root)
            assertThat(SessionLock.held(session)).isTrue()
        }
    }

    @Test fun `exports bounded tails with a manifest and excludes symlinks`() {
        val log = root.resolve("server.log")
        Files.writeString(log, "a".repeat(600_000) + "END")
        val secret = root.resolve("other.txt").also { Files.writeString(it, "outside input") }
        val link = root.resolve("linked.log").also { Files.createSymbolicLink(it, secret) }
        val archive = ServerLogs.archive(listOf(log, link), mapOf("pid" to 42))
        val contents =
            ZipInputStream(ByteArrayInputStream(Base64.getDecoder().decode(archive.getValue("base64")))).use { zip ->
                buildMap { generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes()) } }
            }
        assertThat(contents.keys).containsExactly("0-server.log", "manifest.json")
        assertThat(contents.getValue("0-server.log").size).isEqualTo(512 * 1024)
        assertThat(String(contents.getValue("0-server.log"))).endsWith("END")
        val manifest = JsonParser.parseString(String(contents.getValue("manifest.json"))).asJsonObject
        assertThat(
            manifest["files"]
                .asJsonArray
                .single()
                .asJsonObject["truncated"]
                .asBoolean,
        ).isTrue()
        assertThat(manifest["service"].asJsonObject["pid"].asInt).isEqualTo(42)
    }

    @Test fun `retention rejects impossible limits`() {
        assertThatThrownBy { LogRetention(totalSizeMb = 1) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LogRetention(historyDays = 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LogRetention(retainedSessions = 0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    /** Dates a session's files and directory [time], so that only its liveness keeps it from expiring. */
    private fun age(
        session: Path,
        time: Instant,
    ) {
        Files.list(session).use { files -> files.forEach { Files.setLastModifiedTime(it, FileTime.from(time)) } }
        Files.setLastModifiedTime(session, FileTime.from(time))
    }

    /** A [SessionOwner] in a process of its own, whose log root is [root], until its input ends. */
    private inner class Owner(
        mode: String,
    ) : AutoCloseable {
        val process: Process =
            ProcessBuilder(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElseThrow(),
                "-Dxtc.logs.directory=$root",
                "-cp",
                System.getProperty("java.class.path"),
                SessionOwner::class.java.name,
                mode,
            ).redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        private val input = process.outputWriter()
        private val output = process.inputReader()

        /** Sends [line], when there is one, and returns the owner's next line. */
        fun reply(line: String? = null): String {
            line?.let {
                input.write("$it\n")
                input.flush()
            }
            return checkNotNull(output.readLine()) { "Session owner ${process.pid()} ended" }
        }

        override fun close() {
            input.close()
            if (!process.awaited()) process.destroyForcibly()
            output.close()
        }
    }
}
