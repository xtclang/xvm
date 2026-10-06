package org.xtclang.idea.lsp

import com.google.gson.GsonBuilder
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Last launch for this project, persisted locally across IDE restarts, never a shared settings file. */
@Service(Service.Level.PROJECT)
@State(name = "EcstasySupportLogs", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class ServerSupportLogs : SerializablePersistentStateComponent<ServerSupportLogs.Session>(Session()) {
    data class Session(
        @JvmField val id: String = "",
        @JvmField val started: String = "",
        @JvmField val launcher: String = "No Ecstasy server launch recorded.",
        @JvmField val directory: String = "",
        @JvmField val traceDirectory: String = "",
    )

    fun begin(description: String): String =
        UUID.randomUUID().toString().also { id ->
            updateState { Session(id, Instant.now().toString(), description.takeLast(LAUNCH_LIMIT)) }
        }

    fun append(
        id: String,
        text: String,
    ) {
        updateState { if (it.id == id) it.copy(launcher = (it.launcher + text).takeLast(LAUNCH_LIMIT)) else it }
    }

    fun process(
        id: String,
        pid: Long,
    ) {
        val started = ProcessHandle.of(pid).flatMap { it.info().startInstant() }.orElse(null) ?: return
        val root =
            System.getProperty("xtc.logs.directory")
                ?: System.getenv("XTC_LSP_LOG_DIR")
                ?: "${System.getProperty("user.home")}/.xtc/logs/lsp"
        val directory = Path.of(root).resolve("server-$pid-${started.toEpochMilli()}").toString()
        val traces = System.getProperty("xtc.trace.directory") ?: System.getenv("XTC_LSP_TRACE_DIR") ?: directory
        updateState { if (it.id == id) it.copy(directory = directory, traceDirectory = traces) else it }
    }

    fun export(destination: Path) = archive(state, destination)

    companion object {
        private const val LAUNCH_LIMIT = 64 * 1024
        private const val FILE_LIMIT = 512 * 1024
        private const val FILE_COUNT = 8
        private val sessionName = Regex("server-([0-9]+-[0-9]+)")
        private val logName = Regex("(?:server(?:\\.[0-9.-]+)?\\.log|lsp-trace-[0-9.-]+\\.jsonl)")

        /** Export only a recorded connection's files; pruning may remove files while reading them. */
        internal fun archive(
            session: Session,
            destination: Path,
        ) {
            val process =
                session.directory
                    .takeIf(String::isNotBlank)
                    ?.let { sessionName.matchEntire(Path.of(it).fileName.toString()) }
                    ?.groupValues
                    ?.get(1)
            val files =
                if (process == null) {
                    emptyList()
                } else {
                    listOf(session.directory, session.traceDirectory)
                        .distinct()
                        .flatMap { directory ->
                            val root = Path.of(directory)
                            if (!Files.isDirectory(root, NOFOLLOW_LINKS)) {
                                emptyList()
                            } else {
                                Files.list(root).use { paths ->
                                    paths
                                        .filter { path ->
                                            val name = path.fileName.toString()
                                            Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                                                if (directory == session.directory) {
                                                    logName.matches(name)
                                                } else {
                                                    name.startsWith("lsp-trace-$process") && name.endsWith(".jsonl")
                                                }
                                        }.toList()
                                }
                            }
                        }.sortedByDescending { it.fileName.toString() }
                        .take(FILE_COUNT)
                }
            ZipOutputStream(Files.newOutputStream(destination)).use { zip ->
                fun entry(
                    name: String,
                    bytes: ByteArray,
                ) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                val included =
                    files.mapIndexedNotNull { index, path ->
                        val tail =
                            runCatching {
                                Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                                    val size = channel.size()
                                    val bytes = ByteBuffer.allocate(minOf(size, FILE_LIMIT.toLong()).toInt())
                                    channel.position((size - FILE_LIMIT).coerceAtLeast(0))
                                    while (bytes.hasRemaining() && channel.read(bytes) >= 0) { /* bounded tail */ }
                                    size to bytes.array().copyOf(bytes.position())
                                }
                            }.getOrNull() ?: return@mapIndexedNotNull null
                        val name = "$index-${path.fileName}"
                        entry(name, tail.second)
                        mapOf(
                            "entry" to name,
                            "originalBytes" to tail.first,
                            "includedBytes" to tail.second.size,
                            "truncated" to (tail.first > tail.second.size),
                        )
                    }
                val launcherBytes = session.launcher.toByteArray()
                entry("launcher.log", launcherBytes.copyOfRange((launcherBytes.size - LAUNCH_LIMIT).coerceAtLeast(0), launcherBytes.size))
                entry(
                    "manifest.json",
                    GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(
                            mapOf(
                                "schemaVersion" to 1,
                                "offline" to true,
                                "started" to session.started,
                                "directory" to session.directory,
                                "files" to included,
                                "scope" to (
                                    "Last launch recorded by this project; bounded launcher stderr and server log tails. " +
                                        "No source files or IDE protocol consoles."
                                ),
                            ),
                        ).toByteArray(),
                )
            }
        }
    }
}
