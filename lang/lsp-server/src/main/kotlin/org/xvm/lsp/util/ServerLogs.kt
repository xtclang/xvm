package org.xvm.lsp.util

import ch.qos.logback.core.PropertyDefinerBase
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
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
import kotlin.jvm.optionals.getOrNull

/**
 * Validated limits for server log rolling and closed-session cleanup. File and total-size limits configure
 * each rolling appender; age also limits its history. Age and session count govern closed-session pruning,
 * which preserves live sessions even when those retention targets are exceeded.
 */
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

/**
 * A server's hold on its session directory: an OS lock on the session's [NAME], which the system releases when
 * the process ends, however it ends. Pruning takes a session whose lock nobody holds for a closed one, whatever the
 * PID and start time in its name say: the start time of a PID is computed by each JVM, which on Linux reads the
 * boot time once, so after a step of the clock two JVMs disagree about it, and a PID can be reused. The lock is
 * advisory on Linux and macOS, which is enough: every server takes and tests it the same way. A server holds the
 * folder of its library sources' read-only copies the same way, and shares the folder of its bundled libraries with
 * the other servers of its build, for the same cleanup, each by a lock file [beside] the folder.
 */
internal object SessionLock {
    const val NAME = "server.lock"

    /**
     * The lock file of a [directory] that other code may read every file of, beside it rather than in it: on Linux and
     * macOS a process that opens and closes a file it holds a lock on, by any channel or stream, releases the lock. A
     * compile does so in the library source copies' folder, whose files it reads to fingerprint a module's resources,
     * which let other servers take a running server's folder for a killed one's and delete it.
     */
    fun beside(directory: Path): Path = directory.resolveSibling("${directory.fileName}.lock")

    /**
     * Locks [lock], by default the directory's [NAME], for the rest of this process, creating the directory and the
     * lock file; null when it cannot. A lock file that cannot be locked, on a file system without locks, is removed,
     * so that pruning judges the session by its name as it does one written before servers took the lock.
     */
    fun hold(
        directory: Path,
        lock: Path = directory.resolve(NAME),
    ): FileLock? =
        try {
            Files.createDirectories(directory)
            val channel = FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                channel.tryLock().also { if (it == null) channel.close() }
            } catch (failure: IOException) {
                channel.close()
                Files.deleteIfExists(lock)
                throw failure
            }
        } catch (_: IOException) {
            null
        }

    /**
     * Takes a shared lock on [lock] for the rest of this process, creating it, for a directory that servers use
     * together; null when it cannot. [held] is true of the directory while any process holds it.
     */
    fun share(lock: Path): FileLock? =
        try {
            val channel = FileChannel.open(lock, CREATE, READ, WRITE, NOFOLLOW_LINKS)
            try {
                channel.tryLock(0, Long.MAX_VALUE, true).also { if (it == null) channel.close() }
            } catch (failure: IOException) {
                channel.close()
                throw failure
            }
        } catch (_: IOException) {
            null
        }

    /**
     * Opens and locks [file], creating it: the lock that servers make their directories of one kind under, and delete
     * the [unheld] ones, so that no server sees another's directory before that one is held. Closing the channel
     * releases it. Null when the file system has no locks; then nothing is to be deleted.
     */
    fun guard(file: Path): FileChannel? {
        val channel =
            try {
                FileChannel.open(file, CREATE, WRITE, NOFOLLOW_LINKS)
            } catch (_: IOException) {
                return null
            }
        return try {
            channel.lock()
            channel
        } catch (_: IOException) {
            channel.close()
            null
        } catch (_: OverlappingFileLockException) {
            channel.close()
            null
        }
    }

    /**
     * The other directories beside [own] whose names match [glob], which the same user owns and whose lock [beside]
     * them no process holds: those of servers that ended. One without that lock file, which a server wrote before
     * servers took it there, is not among them. Asked under the [guard] of their kind, whose holder alone makes one.
     */
    fun unheld(
        own: Path,
        glob: String,
    ): List<Path> =
        try {
            val owner = Files.getOwner(own, NOFOLLOW_LINKS)
            Files.newDirectoryStream(own.parent, glob).use { entries ->
                entries.filter { entry ->
                    entry != own &&
                        Files.isDirectory(entry, NOFOLLOW_LINKS) &&
                        Files.getOwner(entry, NOFOLLOW_LINKS) == owner &&
                        held(entry, beside(entry)) == false
                }
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: UnsupportedOperationException) {
            // a file system without owners
            emptyList()
        }

    /**
     * Whether a process holds the session's lock, [lock], by default its [NAME], or null when the session has no lock
     * file, as one written before servers took it, or its lock cannot be tested. A process must not ask about a session
     * it holds: on Linux and macOS, closing any channel to the file releases the process's locks on it.
     */
    fun held(
        directory: Path,
        lock: Path = directory.resolve(NAME),
    ): Boolean? {
        if (!Files.isRegularFile(lock, NOFOLLOW_LINKS)) return null
        return try {
            FileChannel.open(lock, WRITE, NOFOLLOW_LINKS).use { channel ->
                try {
                    channel.tryLock()?.use { false } ?: true
                } catch (_: OverlappingFileLockException) {
                    true
                }
            }
        } catch (_: IOException) {
            null
        }
    }
}

/**
 * Locates, prunes and exports this server's logs and execution traces. Each process gets its own
 * session directory; concurrent servers share only the root and the lock used for pruning.
 *
 * Cleanup removes closed sessions only when every entry is a recognized regular log file or the session's
 * [SessionLock]. Unknown files, symlinks and live servers keep a directory intact. Export reads bounded tails
 * from the current session and configured trace directory, so a support bundle cannot grow with the log files.
 */
internal object ServerLogs {
    val process = TraceProcessId().propertyValue
    val root: Path =
        Path.of(
            System.getProperty(
                "xtc.logs.directory",
            ) ?: System.getenv("XTC_LSP_LOG_DIR") ?: "${System.getProperty("user.home")}/.xtc/logs/lsp",
        )
    val directory: Path = root.resolve("server-$process")

    // this process's lock on its session, held while it runs; the reference keeps its channel open
    private val session: FileLock? = SessionLock.hold(directory)
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

    /**
     * Removes expired or excess closed sessions and returns the number deleted. A held retention lock
     * makes this call a no-op. A session whose [SessionLock] a process holds is kept, and so is this
     * process's own; a session without a lock file is judged by [active], from its name's PID and start
     * time: a live PID with that start time, or an unknown one, keeps it.
     */
    fun prune(
        root: Path = this.root,
        policy: LogRetention = retention,
        now: Instant = Instant.now(),
        active: (Long, Long) -> Boolean = ::isActive,
    ): Int {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return 0
        return FileChannel.open(root.resolve(".retention.lock"), CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
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
                .filter { (path, name) ->
                    val pid = name.groupValues[1].toLongOrNull()
                    val started = name.groupValues[2].toLongOrNull()
                    // this process's own session is live, and testing its lock would release it
                    pid != null && pid > 0 && started != null && path != directory &&
                        !(SessionLock.held(path) ?: active(pid, started))
                }.map { (path, _) -> path to Files.list(path).use { files -> files.toList() } }
                .filter { (_, files) ->
                    files.all {
                        Files.isRegularFile(it, NOFOLLOW_LINKS) &&
                            it.fileName.toString().let { name -> logName.matches(name) || name == SessionLock.NAME }
                    }
                }.map { (path, files) ->
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
    ): Boolean {
        val process = ProcessHandle.of(pid).getOrNull() ?: return false
        if (!process.isAlive) return false
        val actualStart = process.info().startInstant().getOrNull()
        return actualStart == null || actualStart.toEpochMilli() == started
    }

    /** Packages recent log tails and the supplied service status as a bounded, base64-encoded ZIP. */
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
