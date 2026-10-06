package org.xvm.lsp.util

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipInputStream

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
}
