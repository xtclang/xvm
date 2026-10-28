package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.LinkedRepository
import org.xvm.asm.ModuleRepository
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.util.Severity
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Writer
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess

/**
 * Entry point of the execution worker JVM. The language server starts it on demand and exchanges one
 * JSON object per line over stdin and stdout. Program output, including anything the runtime writes to
 * the process streams directly, travels as events and so can never corrupt the protocol. Stdin closing,
 * which is also what happens when the language server exits or dies, stops any run and ends the worker.
 *
 * Commands: `run` (`id`, `modulePath`, `module`, `method`, `args`, `rootDir`), `stop` (`id`), `shutdown`.
 * Events: `ready` (`pid`), `started` (`id`), `output` (`id`, `text`), `exit` (`id`, `code`, `error`), `log`
 * (`text`).
 */
object RunWorker {
    @JvmStatic
    fun main(args: Array<String>) {
        val channel = RunWorkerChannel(PrintStream(FileOutputStream(FileDescriptor.out), false, Charsets.UTF_8))
        val runs = Runs(channel)
        System.setOut(PrintStream(StrayOutput(channel, runs::current), true, Charsets.UTF_8))
        System.setErr(PrintStream(StrayOutput(channel, runs::current), true, Charsets.UTF_8))
        XdkLibraries.configure()
        channel.send("ready") { addProperty("pid", ProcessHandle.current().pid()) }

        BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val command = JsonParser.parseString(line).asJsonObject
                when (command["command"]?.asString) {
                    "run" -> runs.start(command)
                    "stop" -> runs.stop(command["id"].asLong)
                    "shutdown" -> break
                    else -> channel.log("Unknown worker command: $line")
                }
            }
        }
        runs.stopAll()
        // runtime service threads are not daemons, so a finished worker exits explicitly
        exitProcess(0)
    }

    /** One run at a time: each is a fresh application container in the shared runtime. */
    private class Runs(
        private val channel: RunWorkerChannel,
    ) {
        private val active = ConcurrentHashMap<Long, EmbeddingSupport.Control>()

        @Volatile
        private var latest: Long? = null

        fun current(): Long? = latest

        fun start(command: JsonObject) {
            val id = command["id"].asLong
            if (active.isNotEmpty()) {
                channel.exit(id, null, "Another program is still running")
                return
            }
            val errors = ErrorList(100)
            val console = PrintWriter(OutputEvents(channel, id), true)
            val control =
                try {
                    val repositories = command.strings("modulePath").map { ModuleRepository.of(File(it), true) }
                    EmbeddingSupport.instance().runMethod(
                        LinkedRepository(*repositories.toTypedArray()),
                        command["module"].asString,
                        null,
                        EmbeddingSupport.EntryPoint(command.string("method") ?: "run", command.strings("args")),
                        console,
                        EmbeddingSupport.ConsoleInput.NONE,
                        command.string("rootDir")?.let(::File),
                        errors,
                    )
                } catch (failure: Exception) {
                    channel.exit(id, null, failure.toString())
                    return
                }
            if (control == null) {
                channel.exit(id, null, errors.failure() ?: "The module could not be started")
                return
            }

            active[id] = control
            latest = id
            channel.send("started") { addProperty("id", id) }
            Thread
                .ofPlatform()
                .name("xtc-run-$id")
                .daemon(true)
                .start {
                    try {
                        control.join()
                        control.close()
                    } finally {
                        console.flush()
                        active.remove(id)
                        channel.exit(id, control.result(), errors.failure())
                    }
                }
        }

        fun stop(id: Long) {
            // closing kills the task and waits for it, so keep the command reader responsive
            active[id]?.let { control -> Thread.ofVirtual().start { control.close() } }
        }

        fun stopAll() {
            active.values.forEach { runCatching { it.close() } }
        }

        private fun ErrorList.failure(): String? =
            errors
                .filter { it.severity.isAtLeast(Severity.ERROR) }
                .joinToString("\n") { it.message }
                .ifEmpty { null }
    }

    /** Program console output for one run. */
    private class OutputEvents(
        private val channel: RunWorkerChannel,
        private val id: Long,
    ) : Writer() {
        override fun write(
            buffer: CharArray,
            offset: Int,
            length: Int,
        ) {
            if (length > 0) channel.output(id, String(buffer, offset, length))
        }

        override fun flush() {}

        override fun close() {}
    }

    /** Text the runtime prints to System.out or System.err: shown with the current run, or logged. */
    private class StrayOutput(
        private val channel: RunWorkerChannel,
        private val current: () -> Long?,
    ) : OutputStream() {
        private val line = ByteArrayOutputStream()

        @Synchronized
        override fun write(byte: Int) {
            line.write(byte)
            if (byte == '\n'.code) flush()
        }

        @Synchronized
        override fun flush() {
            if (line.size() == 0) return
            val text = line.toString(Charsets.UTF_8)
            line.reset()
            current()?.let { channel.output(it, text) } ?: channel.log(text.trimEnd())
        }
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.strings(name: String): List<String> = getAsJsonArray(name)?.map { it.asString }.orEmpty()
}

/** Writes worker events, one JSON object per line, to the protocol stream. */
internal class RunWorkerChannel(
    private val out: PrintStream,
) {
    @Synchronized
    fun send(
        event: String,
        fill: JsonObject.() -> Unit = {},
    ) {
        out.println(JsonObject().apply { addProperty("event", event) }.apply(fill).toString())
        out.flush()
    }

    fun output(
        id: Long,
        text: String,
    ) = send("output") {
        addProperty("id", id)
        addProperty("text", text)
    }

    fun exit(
        id: Long,
        code: Long?,
        error: String?,
    ) = send("exit") {
        addProperty("id", id)
        code?.let { addProperty("code", it) }
        error?.let { addProperty("error", it) }
    }

    fun log(text: String) = send("log") { addProperty("text", text) }
}
