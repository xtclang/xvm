package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.xvm.api.DebuggerProvider
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.runtime.Debugger
import org.xvm.runtime.SessionDebugger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * The debugger agent of a program that the launcher runs with `--debug`, as in
 * `xec --debug listen:127.0.0.1:5005,suspend,agent=<the language server's jar> App.x`: the launcher finds it in that jar
 * through `META-INF/services`, and a language server's debug session attaches to the program through it, speaking the
 * debug half of the worker protocol ([RunDebuggers]) over a socket, one JSON object per line, as with a run in the
 * execution worker.
 *
 * The options, after the launcher's: `listen:<host>:<port>`, where the agent waits for a debugger to attach, and once
 * one has detached, for the next, while the program runs; port 0 takes a free port, and the agent prints the port it
 * listens on to the program's error stream; or `connect:<host>:<port>`, where a debugger waits for the program. The
 * host must be this machine's, a loopback address. `suspend` holds the program before its first line until a debugger
 * has attached and sent its setup; `token=<token>` refuses a debugger that does not send that token.
 *
 * The exchange: the debugger sends `attach` (`id`, the run ID it uses from then on, and `token`), which the agent
 * answers with `ready` (`pid`, `protocol`, `module`), or with `refused` (`error`), closing the connection; the debugger
 * sends `debug` (`id`, `modules`: the names of the program's own modules besides its main one, and the setup that
 * `run` has: `breakpoints`, `exceptions`, `stopOnEntry` and `functions`), which the agent answers as the worker does
 * a run's setup, then with `started` (`id`); the debugger's commands to the run follow. `detach` (`id`), or the
 * connection closing, drops what the debugger asked for and resumes the program, which runs on; the program's end
 * is an `exit` (`id`, `code`). With no debugger attached, a stop, as at `assert:debug`, is resumed at once. The
 * program's console stays its own: its output and input do not go over the connection.
 */
class XtcDebugAgent : DebuggerProvider {
    override fun start(
        options: String,
        repository: ModuleRepository,
        module: ModuleStructure,
    ): DebuggerProvider.Session = Agent(AgentOptions.of(options), repository, module).also(Agent::open)

    private companion object {
        /** How long closing waits for the agent's thread, which a debugger that does not read could hold up. */
        val CLOSE_LIMIT = 5.seconds
    }

    /** One program's debugging: the program's runtime debugger, and the debugger attached to it, if any. */
    private class Agent(
        private val options: AgentOptions,
        private val repository: ModuleRepository,
        private val module: ModuleStructure,
    ) : DebuggerProvider.Session {
        private val debugger: SessionDebugger =
            SessionDebugger(
                object : SessionDebugger.Listener {
                    override fun stopped(stop: SessionDebugger.Stop) {
                        attached?.listener?.stopped(stop) ?: debugger.resume()
                    }

                    override fun logged(message: String) {
                        attached?.listener?.logged(message)
                    }
                },
            )

        /** The debugger attached to the program, or null while none is. */
        @Volatile
        private var attached: Attached? = null

        /** Completes once a debugger has attached and sent its setup, which a suspended program waits for. */
        private val released = CompletableFuture<Unit>()

        /** Where a debugger attaches, in `listen` mode. */
        private var server: ServerSocket? = null

        /** The connection to the debugger, while there is one. */
        @Volatile
        private var socket: Socket? = null

        /** The program's exit code, once it has ended. */
        @Volatile
        private var code: Int? = null

        @Volatile
        private var closed = false

        /** The thread that waits for a debugger and serves it, which ends before [close] returns. */
        private var thread: Thread? = null

        private class Attached(
            val id: Long,
            val channel: RunWorkerChannel,
            val listener: SessionDebugger.Listener,
        )

        override fun debugger(): Debugger = debugger

        /** Waits for a debugger, or connects to one, and, with `suspend`, until it has sent its setup. */
        fun open() {
            debugger.addModule(module)
            if (options.listen) {
                val server = ServerSocket(options.port, 1, options.address).also { server = it }
                System.err.println("Listening for the debugger at ${options.address.hostAddress}:${server.localPort}")
                daemon {
                    while (!closed) {
                        val socket = runCatching { server.accept() }.getOrNull() ?: break
                        serve(socket)
                    }
                }
            } else {
                val socket = Socket(options.address, options.port)
                daemon { serve(socket) }
            }
            if (options.suspend) {
                try {
                    released.get()
                } catch (failure: ExecutionException) {
                    close()
                    throw failure.cause as? IOException ?: IOException(failure.cause)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    close()
                    throw IOException("Interrupted while waiting for the debugger")
                }
            }
        }

        /** Serves one debugger until it detaches, the connection closes or the program ends. */
        private fun serve(socket: Socket) {
            this.socket = socket
            // a connection that the program's end raced: close() did not see it
            if (closed) {
                runCatching { socket.close() }
                return
            }
            val channel = RunWorkerChannel(PrintStream(socket.getOutputStream(), false, Charsets.UTF_8))
            val lines = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val debuggers = RunDebuggers(channel)
            var id: Long? = null
            try {
                id = hello(lines, channel) ?: return
                while (true) {
                    val command = parse(lines.readLine() ?: break) ?: continue
                    when (command["command"]?.asString) {
                        "debug" -> {
                            // the debugger hears of stops from the first breakpoint it installs
                            attached = Attached(id, channel, debuggers.listener(id))
                            command.getAsJsonArray("modules")?.forEach { name ->
                                repository.loadModule(name.asString)?.takeIf { it !== module }?.let(debugger::addModule)
                            }
                            debuggers.start(id, debugger, command)
                            code?.let { exit(channel, id, it) } ?: channel.send("started") { addProperty("id", id) }
                            released.complete(Unit)
                        }

                        "detach" -> break

                        else -> {
                            val name = command["command"]?.takeIf { it.isJsonPrimitive }?.asString
                            try {
                                if (!debuggers.handle(command)) channel.log("Unknown debugger command: $name")
                            } catch (failure: RuntimeException) {
                                // a command without what it needs fails alone; the debugger stays attached
                                channel.log("The debugger command $name failed: $failure")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // the connection closed, or what came is not the protocol
            } finally {
                // first no stop goes to this debugger, then the program runs on without what it asked for
                attached = null
                id?.let(debuggers::remove)
                debugger.reset()
                this.socket = null
                runCatching { socket.close() }
                if (!options.listen) released.completeExceptionally(IOException("The debugger left before it attached"))
            }
        }

        /** Checks the debugger's `attach` and answers it; the run ID it chose, or null if it is refused. */
        private fun hello(
            lines: BufferedReader,
            channel: RunWorkerChannel,
        ): Long? {
            val hello = lines.readLine()?.let(::parse) ?: return null
            val id = hello["id"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
            val refusal =
                when {
                    hello["command"]?.asString != "attach" || id == null -> "A debugger attaches first, naming its run ID"
                    options.token != null && hello["token"]?.asString != options.token -> "The token does not match the program's"
                    else -> null
                }
            if (refusal != null) {
                channel.send("refused") { addProperty("error", refusal) }
                return null
            }
            channel.send("ready") {
                addProperty("pid", ProcessHandle.current().pid())
                addProperty("protocol", RunWorkerIdentity.PROTOCOL)
                addProperty("module", module.name)
            }
            return id
        }

        override fun ended(code: Int) {
            this.code = code
            attached?.let { exit(it.channel, it.id, code) }
        }

        /** Ends the debugging, and waits for the agent's thread to end: the launcher closes the agent's jar next. */
        override fun close() {
            closed = true
            runCatching { server?.close() }
            runCatching { socket?.close() }
            released.completeExceptionally(IOException("The program ended"))
            thread?.join(CLOSE_LIMIT.toJavaDuration())
        }

        private fun exit(
            channel: RunWorkerChannel,
            id: Long,
            code: Int,
        ) = channel.send("exit") {
            addProperty("id", id)
            addProperty("code", code)
        }

        private fun parse(line: String): JsonObject? = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()

        private fun daemon(task: () -> Unit) {
            thread =
                Thread
                    .ofPlatform()
                    .name("xtc-debug-agent")
                    .daemon(true)
                    .start(task)
        }
    }
}

/**
 * The options of [XtcDebugAgent]: whether it listens or connects, where, whether the program waits for a debugger
 * before its first line, and the token a debugger must send.
 */
internal data class AgentOptions(
    val listen: Boolean,
    val address: InetAddress,
    val port: Int,
    val suspend: Boolean = false,
    val token: String? = null,
) {
    companion object {
        const val USAGE = "--debug takes listen:<host>:<port> or connect:<host>:<port>, then suspend and token=<token>"

        /** The options in `--debug`'s form, without the launcher's own; fails with the reason when they are not. */
        fun of(options: String): AgentOptions {
            val parts = options.split(',').map(String::trim).filter(String::isNotEmpty)
            val where = parts.firstOrNull() ?: throw IllegalArgumentException(USAGE)
            val mode = where.substringBefore(':')
            require(mode == "listen" || mode == "connect") { USAGE }
            val host = where.substringAfter(':', "").substringBeforeLast(':', "")
            val port = where.substringAfterLast(':').toIntOrNull()?.takeIf { it in 0..65535 }
            require(host.isNotEmpty() && port != null) { USAGE }
            require(mode == "listen" || port != 0) { "A debugger to connect to has a port of its own, not 0" }
            val address =
                try {
                    InetAddress.getByName(host)
                } catch (_: UnknownHostException) {
                    throw IllegalArgumentException("Unknown host '$host'")
                }
            require(address.isLoopbackAddress) { "The debugger attaches on this machine only, and '$host' is not a loopback address" }
            var suspend = false
            var token: String? = null
            parts.drop(1).forEach { part ->
                when {
                    part == "suspend" -> suspend = true
                    part.startsWith("token=") -> token = part.substringAfter('=')
                    else -> throw IllegalArgumentException("Unknown --debug option '$part'; $USAGE")
                }
            }
            return AgentOptions(mode == "listen", address, port, suspend, token)
        }
    }
}
