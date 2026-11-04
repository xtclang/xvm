package org.xtclang.idea.lsp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * One event of a test run, as xunit's `json` report prints it in a test process's output, a line of its own, the
 * [MARKER] and then the event as a JSON object, and as the language server sends it apart from a run's output:
 * `xtc/testEvent` for an embedded run, the DAP event `xtcTest` for a debug session. xunit's `JsonExecutionListener`
 * defines the events: `run`, first, with the format's [version]; `started`, a test or container ([container]) [id]
 * under the container [parent], which [selector] runs alone; `finished`, with its [status], `passed`, `failed`,
 * `aborted` or `skipped`, a failure's [message] and [details], and [durationMillis]; and `skipped`, one that did not
 * run, with the reason as [message]. `XtcTestEventsConverter` shows them in IntelliJ's test console, which passes over
 * the events and fields it does not know.
 */
data class TestEvent(
    val event: String,
    val version: Int? = null,
    val id: String? = null,
    val parent: String? = null,
    val name: String? = null,
    val selector: String? = null,
    val container: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val details: String? = null,
    val durationMillis: Long? = null,
) {
    /** The event as a line of a test process's output, as the test console reads it. */
    fun line(): String = "$MARKER${gson.toJson(this)}\n"

    companion object {
        /** How the line of each event starts, ahead of its JSON object. */
        const val MARKER = "##xunit "

        /** The report that prints the events: `testXtc`'s `--test-report`, the injection `xvm.xunit.report`. */
        const val REPORT = "json"

        const val STARTED = "started"
        const val FINISHED = "finished"
        const val SKIPPED = "skipped"

        private val gson = Gson()

        /** The event that [line], a line of a test process's output, is, or null for any other output. */
        fun parse(line: String): TestEvent? =
            line
                .trimEnd('\n', '\r')
                .takeIf { it.startsWith(MARKER) }
                ?.let { text -> runCatching { JsonParser.parseString(text.substring(MARKER.length)) }.getOrNull() }
                ?.let(::of)

        /** The event that [json], an event's object, is, or null for anything else. */
        fun of(json: JsonElement): TestEvent? =
            json
                .takeIf { element ->
                    element.isJsonObject &&
                        element.asJsonObject.get("event")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString } == true
                }?.let {
                    try {
                        gson.fromJson(it, TestEvent::class.java)
                    } catch (_: JsonSyntaxException) {
                        null
                    }
                }
    }
}
