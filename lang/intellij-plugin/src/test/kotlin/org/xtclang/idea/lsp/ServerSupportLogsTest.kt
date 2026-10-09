package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class ServerSupportLogsTest {
    @TempDir lateinit var temporary: Path

    @Test fun `retired callbacks cannot replace a new project launch`() {
        val store = ServerSupportLogs()
        val old = store.begin("Old launch")
        val current = store.begin("Current launch")
        store.append(old, " Stale failure")
        store.append(current, " Current failure")
        val restored = ServerSupportLogs().apply { loadState(store.state) }
        assertThat(restored.state.launcher).isEqualTo("Current launch Current failure")
        assertThat(ServerSupportLogs().state.id).isEmpty()
    }

    @Test fun `offline archive is session owned bounded and does not follow links`() {
        val session = Files.createDirectory(temporary.resolve("server-123-456"))
        val other = Files.createDirectory(temporary.resolve("server-789-456"))
        Files.writeString(other.resolve("server.log"), "OTHER PROJECT")
        Files.writeString(session.resolve("server.log"), "x".repeat(700_000) + "TAIL")
        Files.writeString(session.resolve("Main.x"), "SOURCE CONTENT")
        Files.createSymbolicLink(session.resolve("server.1.log"), other.resolve("server.log"))
        val traces = Files.createDirectory(temporary.resolve("traces"))
        Files.writeString(traces.resolve("lsp-trace-123-456.jsonl"), "OWN TRACE")
        Files.writeString(traces.resolve("lsp-trace-789-456.jsonl"), "OTHER TRACE")
        val destination = temporary.resolve("support.zip")
        ServerSupportLogs.archive(
            ServerSupportLogs.Session(
                launcher = "FAILED LAUNCH",
                directory = session.toString(),
                traceDirectory = traces.toString(),
            ),
            destination,
        )
        ZipFile(destination.toFile()).use { zip ->
            val entries =
                zip.entries().asSequence().associate { entry ->
                    entry.name to
                        zip.getInputStream(entry).use { it.readBytes().decodeToString() }
                }
            assertThat(entries.keys).hasSize(4)
            assertThat(entries.filterKeys { it.endsWith("server.log") }.values.single()).hasSize(512 * 1024).endsWith("TAIL")
            assertThat(entries.values.joinToString()).doesNotContain("OTHER PROJECT", "OTHER TRACE", "SOURCE CONTENT")
            assertThat(entries.getValue("manifest.json")).contains("\"offline\": true", "\"truncated\": true")
        }
    }

    @Test fun `failure before a server process exists still exports launcher information`() {
        val store = ServerSupportLogs()
        val launch = store.begin("Java not found")
        store.append(launch, "z".repeat(100_000))
        val destination = temporary.resolve("failed.zip")
        store.export(destination)
        ZipFile(destination.toFile()).use { zip ->
            assertThat(zip.getEntry("launcher.log").size).isEqualTo(64 * 1024L)
            assertThat(zip.size()).isEqualTo(2)
        }
    }
}
