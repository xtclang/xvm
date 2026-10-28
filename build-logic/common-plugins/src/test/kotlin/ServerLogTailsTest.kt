import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND

class ServerLogTailsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `oversized lines keep moving without splitting UTF-8 characters`() {
        val log = Files.createDirectory(root.resolve("server-1-1")).resolve("server.log")
        val text = "é".repeat(40_000)
        Files.writeString(log, "$text\n")
        val first = ServerLogTails.poll(root, emptyMap())
        val second = ServerLogTails.poll(root, first.positions)
        assertEquals(text, (first.lines + second.lines).joinToString(""))
    }

    @Test
    fun `tail skips old output follows new sessions and handles rollover`() {
        val first = Files.createDirectory(root.resolve("server-1-1")).resolve("server.log")
        Files.writeString(first, "old output\n")
        val initial = ServerLogTails.poll(root, emptyMap(), skipExisting = true)
        assertEquals(emptyList<String>(), initial.lines)
        Files.writeString(first, "héllo\npartial", APPEND)
        val appended = ServerLogTails.poll(root, initial.positions)
        assertEquals(listOf("héllo"), appended.lines)
        assertEquals(emptyList<String>(), ServerLogTails.poll(root, appended.positions).lines)
        Files.writeString(first, " line\n", APPEND)
        val completed = ServerLogTails.poll(root, appended.positions)
        assertEquals(listOf("partial line"), completed.lines)
        Files.move(first, first.resolveSibling("server.2026-10-06.0.log"))
        Files.writeString(first, "rolled\n")
        val next = Files.createDirectory(root.resolve("server-2-2")).resolve("server.log")
        Files.writeString(next, "started\n")
        assertEquals(setOf("rolled", "started"), ServerLogTails.poll(root, completed.positions).lines.toSet())
    }

    @Test
    fun `tail tolerates absent files and excludes foreign directories and symlinks`() {
        assertEquals(emptyMap<Path, ServerLogTails.Position>(), ServerLogTails.poll(root.resolve("absent"), emptyMap()).positions)
        val foreign = Files.createDirectory(root.resolve("foreign")).resolve("server.log")
        Files.writeString(foreign, "unrelated\n")
        Files.createDirectory(root.resolve("server-1-1"))
        Files.createSymbolicLink(root.resolve("server-2-2"), foreign.parent)
        Files.createSymbolicLink(root.resolve("server-1-1/server.log"), foreign)
        assertEquals(emptyList<String>(), ServerLogTails.poll(root, emptyMap()).lines)
    }
}
