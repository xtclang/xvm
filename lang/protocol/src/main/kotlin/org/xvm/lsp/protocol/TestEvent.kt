package org.xvm.lsp.protocol

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * One event of a test run, as xunit's `json` report prints it in the test process's output: a line of its own, the
 * [MARKER] and then the event as a JSON object. The language server reads a run's events from its output once, and
 * sends them to the client as they are, apart from the output: `xtc/testEvent` for a run, the DAP event `xtcTest` for
 * a debug session. A client that runs a test process itself, such as a Gradle task, reads the events from its output.
 * xunit's `JsonExecutionListener` defines them; [event] says what each is:
 *
 * - `run`, first, with the format's [version];
 * - `started`: the test or container ([container]) [id] started, under the container [parent], absent for the root; it
 *   is [name]d so, and [selector] runs it alone, as `xtc test`'s `-p`, `-c` and `-t` take it;
 * - `finished`: [id] ended with the [status] `passed`, `failed`, `aborted` or `skipped`, after [durationMillis]; a
 *   failure's [message] and [details], its exception and stack, come with it; a container's status is its own;
 * - `skipped`: a test or container that did not run, with what `started` says of it, and the reason as [message].
 *
 * A client passes over the events and fields it does not know.
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
    /** The event as a line of a test process's output, as [parse] reads it. */
    fun line(): String = "$MARKER${gson.toJson(this)}\n"

    /**
     * The module of the test or container that [id] names: xunit's IDs are its models' `UniqueId.path`,
     * `<module>:<package>.<class>.<method>`, which starts with the module's qualified name, as it declares it; empty
     * without an ID.
     */
    fun module(): String = id?.substringBefore(':', "").orEmpty()

    companion object {
        /** How the line of each event starts, ahead of its JSON object. */
        const val MARKER = "##xunit "

        /** The report that prints the events: `testXtc`'s `--test-report`, the injection `xvm.xunit.report`. */
        const val REPORT = "json"

        const val RUN = "run"
        const val STARTED = "started"
        const val FINISHED = "finished"
        const val SKIPPED = "skipped"

        private val gson = Gson()

        /**
         * The event that [line], a line of a test process's output with or without its line end, is; null for any
         * other output, as a line that starts with the marker but is no event's object is.
         */
        fun parse(line: String): TestEvent? =
            line
                .trimEnd('\n', '\r')
                .takeIf { it.startsWith(MARKER) }
                ?.let { text -> runCatching { JsonParser.parseString(text.substring(MARKER.length)) }.getOrNull() }
                ?.let(::of)

        /** The event that [json], an event's object, is; null for anything else, as an object without an event is. */
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
