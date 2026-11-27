package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * The language servers' traces in [directory], their JSON lines, read as they grow, for what the tour's waits count as
 * work under way: the client's requests that the server has not answered yet, and how many events the work of the step
 * that waits has added. A step's work is what the client sent the server since the step began, a request or a
 * notification such as an edit's, and all that the server did for it; and any compile, which every answer waits for.
 * The server's own traffic is no step's: LSP4IJ's pulls of the workspace's diagnostics, about one a second however idle
 * the IDE is, and the progress they report; what the server starts of its own accord, such as a refresh it sends the
 * client, and its logs; and the tour's own xtc/busy requests, which it leaves out.
 *
 * An event's span is the event's id, its parent's span the one it belongs to, up to the client's message it began
 * with. Only the spans that count are kept, the current step's client messages' and the compiles' until they end: a
 * span whose parent is not kept is no step's and no compile's, as its parent was not. A file is read from where it last
 * stopped, whole lines only, by its file key, which a roll keeps as it renames the file; a file system without file keys,
 * which neither Linux nor macOS is, has a rolled file read again from its start.
 */
internal class ServerTrace(
    private val directory: Path,
) {
    /** How far each file has been read, by its file key, which stays the same as the file rolls. */
    private val offsets = mutableMapOf<Any, Long>()

    /** Each server's spans and requests, by its process id. */
    private val servers = mutableMapOf<Long, Server>()

    /** How many events the trace has had of the steps' work and of compiles: see [moved]. */
    private var moved = 0L

    /** How many events the trace has had of the server's own traffic: see [traffic]. */
    private var traffic = 0L

    /**
     * Begins the next step: what the trace has until now is earlier steps' work, which no wait counts, so their spans and
     * requests go, but the compiles'.
     */
    @Synchronized
    fun begin() {
        read()
        servers.values.forEach { server ->
            server.spans.values.removeIf { !it.compiling }
            server.open.clear()
        }
    }

    /** How many events the trace has of the steps' work, and of compiles, which grows as long as that work goes on. */
    @Synchronized
    fun moved(): Long {
        read()
        return moved
    }

    /** How many events the trace has of the server's own traffic, which no step's wait counts. */
    @Synchronized
    fun traffic(): Long {
        read()
        return traffic
    }

    /**
     * The requests of the step that the server has not answered, by their methods, such as `the server answering
     * textDocument/hover`; empty when there are none. A server that has ended answers none.
     */
    @Synchronized
    fun inHand(): String {
        read()
        servers.keys.removeIf { pid ->
            ProcessHandle
                .of(pid)
                .map(ProcessHandle::isAlive)
                .orElse(false)
                .not()
        }
        val methods =
            servers.values
                .flatMap { it.open.values }
                .groupingBy { it }
                .eachCount()
        return if (methods.isEmpty()) {
            ""
        } else {
            "the server answering " +
                methods.entries.joinToString { (method, count) -> if (count == 1) method else "$count $method" }
        }
    }

    private fun read() {
        runCatching { Files.list(directory).use { paths -> paths.filter { it.toString().endsWith(".jsonl") }.toList() } }
            .getOrDefault(emptyList())
            // a server's rolled files, whose names have the date, before the one it writes, which has the newest events
            .sorted()
            .forEach { file -> runCatching { read(file) } }
    }

    private fun read(file: Path) {
        val key = Files.readAttributes(file, BasicFileAttributes::class.java).fileKey() ?: file
        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            val from = offsets[key] ?: 0L
            if (channel.size() <= from) return
            val bytes = ByteBuffer.allocate((channel.size() - from).toInt())
            channel.read(bytes, from)
            val end = (bytes.position() - 1 downTo 0).firstOrNull { bytes.get(it) == '\n'.code.toByte() } ?: return
            String(bytes.array(), 0, end, Charsets.UTF_8).lineSequence().filter { BUSY !in it }.forEach(::take)
            offsets[key] = from + end + 1
        }
    }

    private fun take(line: String) {
        val event = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull() ?: return
        val pid = event.number("pid")
        val id = event.number("id")
        if (pid == null || id == null) {
            traffic++
            return
        }
        val server = servers.getOrPut(pid) { Server() }
        val span = server.span(id, event)
        if (event.text("kind") == "lsp-request") server.answer(id, span, event)
        // a compile's span, and a span in it, as it ends: what it holds has ended before it
        if (span.compiling && ENDING[event.text("kind")]?.contains(event.text("event")) == true) server.spans.remove(id)
        if (span.client || span.compiling) moved++ else traffic++
    }

    /** The span [id] of this server, which [event] is of: kept when it counts, or as its parent has it, or new. */
    private fun Server.span(
        id: Long,
        event: JsonObject,
    ): Span =
        spans[id] ?: event.number("parent")?.let(spans::get).let { parent ->
            Span(
                client = parent?.client ?: sentByClient(event),
                compiling = parent?.compiling == true || event.text("kind") == "compiler-queue",
            ).also { if (it.client || it.compiling) spans[id] = it }
        }

    /** Keeps the client's request [id], which [event] starts, in [Server.open] while this server answers it. */
    private fun Server.answer(
        id: Long,
        span: Span,
        event: JsonObject,
    ) {
        when {
            span.client && event.text("event") == "start" && event.text("direction") == "from-client" ->
                open[id] = event.text("operation").orEmpty()

            event.text("event") == "end" -> open.remove(id)
        }
    }

    /** Whether [event], the first of a span with no parent, is a message that the client sent, but a background pull. */
    private fun sentByClient(event: JsonObject): Boolean =
        when (event.text("kind")) {
            "lsp-request" -> event.text("direction") == "from-client" && event.text("operation") != WORKSPACE_PULL
            "lsp-notification" -> event.text("event") == "received"
            else -> false
        }

    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.number(name: String): Long? =
        get(name)
            ?.takeIf {
                it.isJsonPrimitive
            }?.asJsonPrimitive
            ?.takeIf { it.isNumber }
            ?.asLong

    /** A server's spans that count, by their ids, and the client's requests of the step that it has not answered yet, by their methods. */
    private class Server {
        val spans = mutableMapOf<Long, Span>()
        val open = mutableMapOf<Long, String>()
    }

    /** A span that counts: the work of a message the step's [client] sent, or a compile's, or part of one, if [compiling]. */
    private data class Span(
        val client: Boolean,
        val compiling: Boolean,
    )
}

/** How a trace line of an xtc/busy request names it, which the tour's own waits send. */
private const val BUSY = "\"operation\":\"xtc/busy\""

/** The events that end a span of a compile, by the span's kind: a compile job's, and the compiler's work in it. */
private val ENDING = mapOf("compiler-queue" to setOf("end", "removed"), "javatools" to setOf("end"))

/** LSP4IJ's pull of the workspace's diagnostics, which it repeats about once a second, whatever the IDE does. */
private const val WORKSPACE_PULL = "workspace/diagnostic"
