package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xvm.runtime.debug.DebugProtocol
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetAddress
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * A stand-in for [RunWorker] that speaks its protocol, for supervision tests that need a worker to misbehave. It
 * connects as a worker does ([RunWorker.connect]) and reports the identity [IDENTITY]. With the argument `silent` it
 * never connects and answers nothing; with `mismatch` it reports another XDK, and with `unnumbered` no identity, as a
 * first-protocol worker; with `dies` it writes [DIES] to its standard error and exits with 3 before it connects; with
 * `wrongToken` it first connects with another token, and connects as a worker does once that connection is closed;
 * with `idleFirst` it first makes a connection that sends nothing and stays open, then connects as a worker does.
 * The module name says what a run does: `Quick` prints `ok` and exits with 0, `Polite` runs until stopped,
 * `Stubborn` ignores Stop, `Crash` ends the worker process, and `Hoard` and `Spawn` exit with 0 and report
 * 4 GB of heap or 10,000 threads in use afterwards. `Leak`, `Unended` and `Pool` exit with 0 and report
 * a console or a run still held, or one more runtime pool thread than a `Quick` run. Each run that exits reports its
 * usage; normally 1 MB of heap, 10 threads, 8 of them the pool's. `Garbage` sends [MESSAGES] outputs of
 * [MESSAGE_SIZE] characters each, while a thread of its own writes [GARBAGE] to its standard output and error, partial
 * lines and the text of a protocol message among it, and exits with 0 once it is sent input. `Second` tries a second
 * connection to its server's port, prints whether it was refused, and exits with 0. `Raw` writes [RAW] to its standard
 * output, as its JVM's own logging would, prints `ok`, and exits with 0 once it is sent input; the worker then writes
 * [RAW_AFTER] lines more to its standard output and [ERRORS_AFTER] to its standard error as it shuts down, outside any
 * run, the last of which is [LAST_ERROR].
 */
object FakeRunWorker {
    internal val IDENTITY = RunWorkerIdentity(RunWorkerIdentity.PROTOCOL, "fake-xdk")

    /** What the `dies` mode writes to its standard error before it exits. */
    internal const val DIES = "the fake worker cannot start"

    /** What `Garbage` writes to its standard output and error, as lines of the JVM's own would: one ends in an exit. */
    internal val GARBAGE = listOf("garbage before a message, ", "and after it", "{\"event\":\"exit\",\"id\":1,\"code\":7}")

    /** How many outputs `Garbage` sends, and how long each is: longer than a pipe's or a socket's single write. */
    internal const val MESSAGES = 200
    internal const val MESSAGE_SIZE = 16_000

    /** The output that `Garbage` sends as its [index]th. */
    internal fun message(index: Int) = "$index:" + "y".repeat(MESSAGE_SIZE) + "\n"

    /** The line that a `Raw` run writes to its standard output while it runs. */
    internal const val RAW = "[0.004s][info][gc] Using the fake collector"

    /** How many lines a worker that ran `Raw` writes to its standard output as it shuts down. */
    internal const val RAW_AFTER = 30

    /** How many lines a worker that ran `Raw` writes to its standard error as it shuts down, as a crash would. */
    internal const val ERRORS_AFTER = 25

    /** The last of those lines. */
    internal val LAST_ERROR = error(ERRORS_AFTER - 1)

    /** The [index]th line that a worker that ran `Raw` writes to its standard error as it shuts down. */
    internal fun error(index: Int) = "# a fatal error, line $index"

    /** The connection of the `idleFirst` mode that sends nothing, kept open while the worker runs. */
    private var idle: Socket? = null

    /** Whether a `Raw` run ran, so that the worker writes its lines as it shuts down. */
    private var raw = false

    private val running = HashMap<Long?, String>()

    private lateinit var channel: PrintStream

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()
        when (mode) {
            // never ready: answers nothing until it is ended
            "silent" -> {
                System.`in`.readAllBytes()
                exitProcess(0)
            }

            "dies" -> {
                System.err.println(DIES)
                System.err.flush()
                exitProcess(3)
            }

            "wrongToken" -> refusedConnection()

            "idleFirst" -> idle = Socket(InetAddress.getByName(RunWorker.LOOPBACK), System.getenv(RunWorker.PORT).toInt())
        }
        channel = RunWorker.connect()
        val pid = ProcessHandle.current().pid()
        send(
            when (mode) {
                "mismatch" -> IDENTITY.copy(xdk = "other-xdk").ready(pid)
                "unnumbered" -> DebugProtocol.Ready(pid, null, null, null)
                else -> IDENTITY.ready(pid)
            },
        )
        BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val command = JsonParser.parseString(line).asJsonObject
                val id = command["id"]?.asLong
                when (command["command"]?.asString) {
                    "run" -> {
                        send(DebugProtocol.Started(id!!))
                        when (val module = command["module"].asString) {
                            "Quick", "Hoard", "Spawn", "Leak", "Unended", "Pool" -> {
                                output(id, "ok\n")
                                send(DebugProtocol.Exit(id, 0L, null))
                                usage(
                                    id,
                                    heap = if (module == "Hoard") 4L shl 30 else 1L shl 20,
                                    threads = if (module == "Spawn") 10_000 else 10,
                                    poolThreads = if (module == "Pool") 9 else 8,
                                    consoles = if (module == "Leak") 1 else 0,
                                    runs = if (module == "Unended") 1 else 0,
                                )
                            }

                            "Garbage" -> garbage(id)

                            "Raw" -> {
                                println(RAW)
                                System.out.flush()
                                output(id, "ok\n")
                            }

                            "Second" -> {
                                output(id, if (secondConnection()) "second connection taken\n" else "second connection refused\n")
                                send(DebugProtocol.Exit(id, 0L, null))
                                usage(id, heap = 1L shl 20, threads = 10)
                            }

                            "Crash" -> exitProcess(3)

                            else -> output(id, "running\n")
                        }
                    }

                    "input" ->
                        if (running[id] == "Garbage" || running[id] == "Raw") {
                            raw = raw || running[id] == "Raw"
                            send(DebugProtocol.Exit(id!!, 0L, null))
                            usage(id, heap = 1L shl 20, threads = 10)
                        }

                    "stop" ->
                        if (running[id] != "Stubborn") {
                            send(DebugProtocol.Exit(id!!, null, "stopped"))
                            usage(id, heap = 1L shl 20, threads = 10)
                        }

                    "shutdown" -> {
                        if (raw) {
                            repeat(RAW_AFTER) { println("[0.1s][info][gc] outside a run $it") }
                            System.out.flush()
                            repeat(ERRORS_AFTER) { System.err.println(error(it)) }
                            System.err.flush()
                        }
                        break
                    }
                }
                if (command["command"]?.asString == "run") running[id] = command["module"].asString
            }
        }
        exitProcess(0)
    }

    /**
     * Sends [MESSAGES] long outputs while a thread writes [GARBAGE] to the standard output and error, byte by byte and
     * flushed, so that it lands between and within whatever else is written there; then waits for input to exit.
     */
    private fun garbage(id: Long) {
        val writer =
            thread(name = "fake-worker-garbage") {
                repeat(MESSAGES) {
                    listOf(System.out, System.err).forEach { stream ->
                        GARBAGE.joinToString("").forEach { char ->
                            stream.print(char)
                            stream.flush()
                        }
                        stream.println()
                        stream.flush()
                    }
                }
            }
        repeat(MESSAGES) { output(id, message(it)) }
        writer.join()
    }

    /** Connects with a token that is not the worker's, and waits until the server closes that connection. */
    private fun refusedConnection() {
        Socket(InetAddress.getByName(RunWorker.LOOPBACK), System.getenv(RunWorker.PORT).toInt()).use { socket ->
            socket.getOutputStream().write("not-the-token\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().read()
        }
    }

    /** Whether a second connection to the server's port, after the worker's own, is taken. */
    private fun secondConnection(): Boolean =
        try {
            Socket(InetAddress.getByName(RunWorker.LOOPBACK), System.getenv(RunWorker.PORT).toInt()).use { true }
        } catch (_: IOException) {
            false
        }

    private fun output(
        id: Long?,
        text: String,
    ) = send("output") {
        addProperty("id", id)
        addProperty("text", text)
    }

    private fun usage(
        id: Long?,
        heap: Long,
        threads: Int,
        poolThreads: Int = 8,
        consoles: Int = 0,
        runs: Int = 0,
    ) = send("usage") {
        addProperty("id", id)
        addProperty("heapUsed", heap)
        addProperty("heapMax", 8L shl 30)
        addProperty("threads", threads)
        addProperty("poolThreads", poolThreads)
        addProperty("consoles", consoles)
        addProperty("runs", runs)
    }

    private fun send(
        event: String,
        fill: JsonObject.() -> Unit,
    ) = send(JsonObject().apply { addProperty("event", event) }.apply(fill))

    private fun send(event: DebugProtocol.Event) = send(DebugProtocol.json(event))

    private fun send(json: JsonObject) {
        channel.println(json.toString())
        channel.flush()
    }
}
