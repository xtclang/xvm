package org.xtclang.idea.dap

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The lines typed in a debug session's console. IntelliJ writes them to the session's process input, which is also
 * the stream that LSP4IJ's DAP client writes its messages to, and LSP4IJ 0.21.0 sends no `evaluate` of its own for
 * them. Each is what a line typed in a debug console is to the debug adapter: an `evaluate` request in the `repl`
 * context, in the top frame of the fiber that stopped, where it runs as code; or, while the program runs, the
 * program's console input. The answer to one is console output, as the adapter's other output is, and the client
 * never sees the request or its response.
 */
internal class DebugConsoleLines {
    private val seq = AtomicInteger(FIRST_SEQ)
    private val asked = ConcurrentHashMap.newKeySet<Int>()

    /** The top frame of the stopped fiber, from the client's first stack trace after the stop; none while it runs. */
    @Volatile
    private var frame: Int? = null

    @Volatile
    private var stopped = false

    /** The request that evaluates a typed [line], without its line end. */
    fun request(line: String): JsonObject {
        val number = seq.incrementAndGet()
        asked += number
        return JsonObject().apply {
            addProperty("seq", number)
            addProperty("type", "request")
            addProperty("command", "evaluate")
            add(
                "arguments",
                JsonObject().apply {
                    addProperty("expression", line.trimEnd('\n', '\r'))
                    addProperty("context", "repl")
                    frame?.let { addProperty("frameId", it) }
                },
            )
        }
    }

    /** Sees a [message] that the client sends, which resumes the program when it is a request that does. */
    fun sent(message: JsonObject) {
        if (message["type"]?.asString == "request" && message["command"]?.asString in RESUMES) running()
    }

    /**
     * Sees a [message] that the session sends, which may stop the program or give its stack, and answers what the
     * client gets for it: the message itself; for the response to a typed line, the console output that shows its
     * answer or its failure, or nothing when it has neither, as the program's input has.
     */
    fun received(message: JsonObject): JsonObject? {
        val type = message["type"]?.asString
        if (type == "event") {
            when (message["event"]?.asString) {
                "stopped" -> {
                    stopped = true
                    frame = null
                }

                "continued", "exited", "terminated" -> running()
            }
            return message
        }
        if (type != "response") return message
        val answered = message["request_seq"]?.asInt
        if (answered != null && asked.remove(answered)) return answer(message)
        if (message["command"]?.asString == "stackTrace" && stopped && frame == null && message["success"]?.asBoolean == true) {
            frame =
                message["body"]
                    ?.asJsonObject
                    ?.getAsJsonArray("stackFrames")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.get("id")
                    ?.asInt
        }
        return message
    }

    private fun running() {
        stopped = false
        frame = null
    }

    /** The console output for the response to a typed line: its result, or its failure's message; none without either. */
    private fun answer(response: JsonObject): JsonObject? {
        val succeeded = response["success"]?.asBoolean == true
        val text =
            if (succeeded) {
                response["body"]?.asJsonObject?.get("result")?.asString
            } else {
                response["message"]?.asString
            }
        if (text.isNullOrEmpty()) return null
        return JsonObject().apply {
            addProperty("seq", seq.incrementAndGet())
            addProperty("type", "event")
            addProperty("event", "output")
            add(
                "body",
                JsonObject().apply {
                    addProperty("category", if (succeeded) "console" else "stderr")
                    addProperty("output", "$text\n")
                },
            )
        }
    }

    private companion object {
        /** Above any sequence number the client gives its own messages, so that a typed line's answer is told apart. */
        const val FIRST_SEQ = 1_000_000_000

        /** The requests after which the program runs on, so that a typed line is its input until it stops again. */
        val RESUMES = setOf("continue", "next", "stepIn", "stepOut", "stepBack", "reverseContinue", "restartFrame", "goto")
    }
}
