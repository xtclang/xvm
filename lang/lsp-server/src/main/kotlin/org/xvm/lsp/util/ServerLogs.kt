package org.xvm.lsp.util

import ch.qos.logback.core.PropertyDefinerBase
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class LogRetention(
    val historyDays: Int = 7,
    val maxFileMb: Int = 10,
    val totalSizeMb: Int = 50,
    val retainedSessions: Int = 5,
) {
    init {
        require(historyDays in 1..90 && maxFileMb in 1..100 && totalSizeMb in maxFileMb..1000 && retainedSessions in 1..20) {
            "Invalid server log retention limits"
        }
    }

    companion object {
        fun read(): LogRetention {
            fun value(
                name: String,
                fallback: Int,
            ) = System.getProperty("xtc.logs.$name")?.toIntOrNull() ?: fallback
            return LogRetention(value("historyDays", 7), value("maxFileMb", 10), value("totalSizeMb", 50), value("retainedSessions", 5))
        }
    }
}

/** Owned session files only. Never traverses source roots, other applications' logs, or symlinks. */
internal object ServerLogs {
    val process = TraceProcessId().propertyValue
    val root: Path = Path.of(System.getProperty("xtc.logs.directory", "${System.getProperty("user.home")}/.xtc/logs/lsp"))
    val directory: Path = root.resolve("server-$process")
    val traceDirectory: Path =
        Path.of(System.getProperty("xtc.trace.directory") ?: System.getenv("XTC_LSP_TRACE_DIR") ?: directory.toString())
    val retention = LogRetention.read()
    private val sessionName = Regex("server-([0-9]+)-([0-9]+)")
    private val logName = Regex("(?:server(?:\\.[0-9.-]+)?\\.log|lsp-trace-[0-9.-]+\\.jsonl)")
    private const val FILE_LIMIT = 512 * 1024
    private const val EXPORT_LIMIT = 4 * 1024 * 1024

    fun status(): Map<String, Any> =
        mapOf(
            "directory" to directory.toString(),
            "traceDirectory" to traceDirectory.toString(),
            "retention" to retention,
            "exportLimitBytes" to EXPORT_LIMIT,
            "exportFileLimitBytes" to FILE_LIMIT,
        )

    /** Active sessions are protected, including a PID with an unknown start time. */
    fun prune(
        root: Path = this.root,
        policy: LogRetention = retention,
        now: Instant = Instant.now(),
        active: (Long, Long) -> Boolean = ::isActive,
    ): Int {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return 0
        return FileChannel.open(root.resolve(".retention.lock"), CREATE, WRITE).use { channel ->
            val lock =
                try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
            lock?.use { pruneClosed(root, policy, now, active) } ?: 0
        }
    }

    private fun pruneClosed(
        root: Path,
        policy: LogRetention,
        now: Instant,
        active: (Long, Long) -> Boolean,
    ): Int {
        val candidates =
            Files
                .list(root)
                .use { paths -> paths.filter { Files.isDirectory(it, NOFOLLOW_LINKS) }.toList() }
                .mapNotNull { path -> sessionName.matchEntire(path.fileName.toString())?.let { path to it } }
                .filterNot { (_, name) -> active(name.groupValues[1].toLong(), name.groupValues[2].toLong()) }
                .map { (path, _) -> path to Files.list(path).use { files -> files.toList() } }
                .filter { (_, files) -> files.all { Files.isRegularFile(it, NOFOLLOW_LINKS) && logName.matches(it.fileName.toString()) } }
                .map { (path, files) ->
                    Triple(
                        path,
                        files,
                        (
                            files.map { Files.getLastModifiedTime(it).toInstant() } +
                                Files.getLastModifiedTime(path).toInstant()
                        ).max(),
                    )
                }.sortedByDescending { it.third }
        return candidates
            .filterIndexed { index, candidate ->
                index >= policy.retainedSessions ||
                    candidate.third.isBefore(now.minusSeconds(policy.historyDays * 86400L))
            }.count { (path, files, _) ->
                files.forEach(Files::deleteIfExists)
                Files.deleteIfExists(path)
            }
    }

    private fun isActive(
        pid: Long,
        started: Long,
    ): Boolean =
        ProcessHandle
            .of(pid)
            .map { process ->
                process.isAlive &&
                    process
                        .info()
                        .startInstant()
                        .map { it.toEpochMilli() == started }
                        .orElse(true)
            }.orElse(false)

    fun export(status: Map<String, Any?>): Map<String, String> {
        val files =
            listOf(directory, traceDirectory)
                .distinct()
                .flatMap { path ->
                    if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
                        Files.list(path).use { stream ->
                            stream
                                .filter { file ->
                                    Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                                        (
                                            if (path ==
                                                directory
                                            ) {
                                                logName.matches(file.fileName.toString())
                                            } else {
                                                file.fileName.toString().startsWith("lsp-trace-$process") &&
                                                    file.fileName.toString().endsWith(".jsonl")
                                            }
                                        )
                                }.toList()
                        }
                    } else {
                        emptyList()
                    }
                }.sortedByDescending { Files.getLastModifiedTime(it).toMillis() }
                .take(EXPORT_LIMIT / FILE_LIMIT)
        return archive(files, status)
    }

    internal fun archive(
        files: List<Path>,
        status: Map<String, Any?>,
    ): Map<String, String> {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val included =
                files.take(EXPORT_LIMIT / FILE_LIMIT).mapIndexedNotNull { index, path ->
                    if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) return@mapIndexedNotNull null
                    // Read at most one tail, even when a producer continues appending during export.
                    val entry =
                        Files.newByteChannel(path, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                            val size = channel.size()
                            val bytes = ByteBuffer.allocate(minOf(size, FILE_LIMIT.toLong()).toInt())
                            channel.position((size - FILE_LIMIT).coerceAtLeast(0))
                            while (bytes.hasRemaining() && channel.read(bytes) >= 0) { /* bounded snapshot */ }
                            val name = "$index-${path.fileName}"
                            zip.putNextEntry(ZipEntry(name))
                            zip.write(bytes.array(), 0, bytes.position())
                            zip.closeEntry()
                            mapOf(
                                "entry" to name,
                                "originalBytes" to size,
                                "includedBytes" to bytes.position(),
                                "truncated" to (size > bytes.position()),
                            )
                        }
                    entry
                }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(
                        mapOf(
                            "schemaVersion" to 1,
                            "createdAt" to Instant.now().toString(),
                            "service" to status,
                            "files" to included,
                            "scope" to
                                "Recent server logs and execution traces; IDE protocol consoles are separate. Each file is a bounded tail and may start mid-line.",
                        ),
                    ).toByteArray(),
            )
            zip.closeEntry()
        }
        return mapOf("fileName" to "ecstasy-server-$process.zip", "base64" to Base64.getEncoder().encodeToString(output.toByteArray()))
    }
}

/** Logback evaluates this before opening either process-owned rolling appender. */
class ServerLogDirectory : PropertyDefinerBase() {
    override fun getPropertyValue(): String = ServerLogs.directory.toString()
}
