package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.xvm.debug.BreakpointRequest
import org.xvm.debug.BreakpointState
import org.xvm.debug.FrameState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState

/**
 * The JSON forms of the debug data that the worker protocol carries between [RunWorkerProcess] in the language
 * server and [RunWorker] in the worker, where the program and its runtime debugger run.
 */
internal object RunWorkerDebug {
    fun sources(sources: List<SourceBreakpoints>) = JsonArray().apply { sources.forEach { add(source(it)) } }

    fun source(source: SourceBreakpoints) =
        JsonObject().apply {
            addProperty("path", source.path)
            add(
                "breakpoints",
                JsonArray().apply {
                    source.breakpoints.forEach { request ->
                        add(
                            JsonObject().apply {
                                addProperty("id", request.id)
                                addProperty("line", request.line)
                            },
                        )
                    }
                },
            )
        }

    fun sourcesOf(json: JsonArray): List<SourceBreakpoints> = json.map { sourceOf(it.asJsonObject) }

    fun sourceOf(json: JsonObject) =
        SourceBreakpoints(
            json["path"].asString,
            json.getAsJsonArray("breakpoints").map { BreakpointRequest(it.asJsonObject["id"].asInt, it.asJsonObject["line"].asInt) },
        )

    fun states(states: List<BreakpointState>) =
        JsonArray().apply {
            states.forEach { state ->
                add(
                    JsonObject().apply {
                        addProperty("id", state.id)
                        addProperty("line", state.line)
                        addProperty("verified", state.verified)
                        state.message?.let { addProperty("message", it) }
                    },
                )
            }
        }

    fun statesOf(json: JsonArray): List<BreakpointState> =
        json.map { it.asJsonObject }.map { state ->
            BreakpointState(state["id"].asInt, state["line"].asInt, state["verified"].asBoolean, state["message"]?.asString)
        }

    fun threads(threads: List<ThreadState>) =
        JsonArray().apply {
            threads.forEach { thread ->
                add(
                    JsonObject().apply {
                        addProperty("id", thread.id)
                        addProperty("name", thread.name)
                    },
                )
            }
        }

    fun threadsOf(json: JsonArray): List<ThreadState> =
        json.map { it.asJsonObject }.map { ThreadState(it["id"].asInt, it["name"].asString) }

    fun frames(frames: List<FrameState>) =
        JsonArray().apply {
            frames.forEach { frame ->
                add(
                    JsonObject().apply {
                        addProperty("id", frame.id)
                        addProperty("name", frame.name)
                        frame.path?.let { addProperty("path", it) }
                        addProperty("line", frame.line)
                    },
                )
            }
        }

    fun framesOf(json: JsonArray): List<FrameState> =
        json.map { it.asJsonObject }.map { FrameState(it["id"].asInt, it["name"].asString, it["path"]?.asString, it["line"].asInt) }

    fun stop(
        event: JsonObject,
        stop: StopState,
    ) {
        event.addProperty("reason", stop.reason)
        stop.description?.let { event.addProperty("description", it) }
        event.addProperty("thread", stop.threadId)
        event.add("breakpoints", JsonArray().apply { stop.breakpointIds.forEach(::add) })
    }

    fun stopOf(event: JsonObject) =
        StopState(
            event["reason"].asString,
            event["description"]?.asString,
            event["thread"].asInt,
            event.getAsJsonArray("breakpoints").map { it.asInt },
        )
}
