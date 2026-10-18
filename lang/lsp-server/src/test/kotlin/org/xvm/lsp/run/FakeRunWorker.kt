package org.xvm.lsp.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * A stand-in for [RunWorker] that speaks its protocol, for supervision tests that need a worker to misbehave.
 * It reports the identity [IDENTITY]. With the argument `silent` it never reports ready and answers nothing;
 * with `mismatch` it reports another XDK, and with `unnumbered` no identity, as a first-protocol worker. The
 * module name says what a run does: `Quick` prints `ok` and exits with 0, `Polite` runs until stopped,
 * `Stubborn` ignores Stop, `Crash` ends the worker process, and `Hoard` and `Spawn` exit with 0 and report
 * 4 GB of heap or 10,000 threads in use afterwards. Each run that exits reports its usage.
 */
object FakeRunWorker {
    internal val IDENTITY = RunWorkerIdentity(RunWorkerIdentity.PROTOCOL, "fake-xdk")

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull()
        if (mode == "silent") {
            // never ready: answers nothing until it is ended
            System.`in`.readAllBytes()
            exitProcess(0)
        }
        send("ready") {
            addProperty("pid", ProcessHandle.current().pid())
            when (mode) {
                "mismatch" -> IDENTITY.copy(xdk = "other-xdk").addTo(this)
                "unnumbered" -> {}
                else -> IDENTITY.addTo(this)
            }
        }
        BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).useLines { lines ->
            for (line in lines) {
                val command = JsonParser.parseString(line).asJsonObject
                val id = command["id"]?.asLong
                when (command["command"]?.asString) {
                    "run" -> {
                        send("started") { addProperty("id", id) }
                        when (val module = command["module"].asString) {
                            "Quick", "Hoard", "Spawn" -> {
                                output(id, "ok\n")
                                send("exit") {
                                    addProperty("id", id)
                                    addProperty("code", 0)
                                }
                                usage(
                                    id,
                                    heap =
                                        if (module ==
                                            "Hoard"
                                        ) {
                                            4L shl 30
                                        } else {
                                            1L shl 20
                                        },
                                    threads = if (module == "Spawn") 10_000 else 10,
                                )
                            }

                            "Crash" -> exitProcess(3)

                            else -> output(id, "running\n")
                        }
                    }

                    "stop" ->
                        if (running[id] != "Stubborn") {
                            send("exit") {
                                addProperty("id", id)
                                addProperty("error", "stopped")
                            }
                            usage(id, heap = 1L shl 20, threads = 10)
                        }

                    "shutdown" -> break
                }
                if (command["command"]?.asString == "run") running[id] = command["module"].asString
            }
        }
        exitProcess(0)
    }

    private val running = HashMap<Long?, String>()

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
    ) = send("usage") {
        addProperty("id", id)
        addProperty("heapUsed", heap)
        addProperty("heapMax", 8L shl 30)
        addProperty("threads", threads)
    }

    private fun send(
        event: String,
        fill: JsonObject.() -> Unit,
    ) {
        println(JsonObject().apply { addProperty("event", event) }.apply(fill).toString())
        System.out.flush()
    }
}
