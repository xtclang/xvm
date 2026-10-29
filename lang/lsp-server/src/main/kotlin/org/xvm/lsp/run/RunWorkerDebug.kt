package org.xvm.lsp.run

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.xvm.debug.BreakpointRequest
import org.xvm.debug.BreakpointState
import org.xvm.debug.CompletionState
import org.xvm.debug.CompletionsState
import org.xvm.debug.DataBreakpointRequest
import org.xvm.debug.ExceptionInfoState
import org.xvm.debug.ExceptionStops
import org.xvm.debug.FrameState
import org.xvm.debug.FunctionBreakpointRequest
import org.xvm.debug.ModuleState
import org.xvm.debug.ScopeState
import org.xvm.debug.SourceBreakpoints
import org.xvm.debug.StepTargetState
import org.xvm.debug.StopState
import org.xvm.debug.ThreadState
import org.xvm.debug.VariableState

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
                                request.condition?.let { addProperty("condition", it) }
                                request.hitCondition?.let { addProperty("hitCondition", it) }
                                request.logMessage?.let { addProperty("logMessage", it) }
                            },
                        )
                    }
                },
            )
        }

    fun sourcesOf(json: JsonArray): List<SourceBreakpoints> = json.map { sourceOf(it.asJsonObject) }

    fun data(data: List<DataBreakpointRequest>) =
        JsonArray().apply {
            data.forEach { request ->
                add(
                    JsonObject().apply {
                        addProperty("id", request.id)
                        addProperty("dataId", request.dataId)
                        request.condition?.let { addProperty("condition", it) }
                        request.hitCondition?.let { addProperty("hitCondition", it) }
                    },
                )
            }
        }

    fun dataOf(json: JsonArray): List<DataBreakpointRequest> =
        json.map { it.asJsonObject }.map {
            DataBreakpointRequest(it["id"].asInt, it["dataId"].asString, it["condition"]?.asString, it["hitCondition"]?.asString)
        }

    fun functions(functions: List<FunctionBreakpointRequest>) =
        JsonArray().apply {
            functions.forEach { request ->
                add(
                    JsonObject().apply {
                        addProperty("id", request.id)
                        addProperty("name", request.name)
                        request.condition?.let { addProperty("condition", it) }
                        request.hitCondition?.let { addProperty("hitCondition", it) }
                    },
                )
            }
        }

    fun functionsOf(json: JsonArray?): List<FunctionBreakpointRequest> =
        json
            ?.map { it.asJsonObject }
            ?.map {
                FunctionBreakpointRequest(
                    it["id"].asInt,
                    it["name"].asString,
                    it["condition"]?.asString,
                    it["hitCondition"]?.asString,
                )
            }.orEmpty()

    fun sourceOf(json: JsonObject) =
        SourceBreakpoints(
            json["path"].asString,
            json.getAsJsonArray("breakpoints").map { it.asJsonObject }.map {
                BreakpointRequest(
                    it["id"].asInt,
                    it["line"].asInt,
                    it["condition"]?.asString,
                    it["hitCondition"]?.asString,
                    it["logMessage"]?.asString,
                )
            },
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
                        state.path?.let { addProperty("path", it) }
                    },
                )
            }
        }

    fun statesOf(json: JsonArray): List<BreakpointState> =
        json.map { it.asJsonObject }.map { state ->
            BreakpointState(
                state["id"].asInt,
                state["line"].asInt,
                state["verified"].asBoolean,
                state["message"]?.asString,
                state["path"]?.asString,
            )
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

    fun modules(modules: List<ModuleState>) =
        JsonArray().apply {
            modules.forEach { module ->
                add(
                    JsonObject().apply {
                        addProperty("name", module.name)
                        module.version?.let { addProperty("version", it) }
                        module.sourceDir?.let { addProperty("sourceDir", it) }
                        add("sources", JsonArray().apply { module.sources.forEach(::add) })
                        addProperty("own", module.own)
                    },
                )
            }
        }

    fun modulesOf(json: JsonArray): List<ModuleState> =
        json.map { it.asJsonObject }.map { module ->
            ModuleState(
                module["name"].asString,
                module["version"]?.asString,
                module["sourceDir"]?.asString,
                module["sources"].asJsonArray.map { it.asString },
                module["own"].asBoolean,
            )
        }

    fun stepTargets(targets: List<StepTargetState>) =
        JsonArray().apply {
            targets.forEach { target ->
                add(
                    JsonObject().apply {
                        addProperty("id", target.id)
                        addProperty("label", target.label)
                    },
                )
            }
        }

    fun stepTargetsOf(json: JsonArray): List<StepTargetState> =
        json.map { it.asJsonObject }.map { StepTargetState(it["id"].asInt, it["label"].asString) }

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
                        frame.module?.let { addProperty("module", it) }
                        frame.source?.let { addProperty("source", it) }
                        if (frame.restartable) addProperty("restartable", true)
                    },
                )
            }
        }

    fun framesOf(json: JsonArray): List<FrameState> =
        json.map { it.asJsonObject }.map {
            FrameState(
                it["id"].asInt,
                it["name"].asString,
                it["path"]?.asString,
                it["line"].asInt,
                it["module"]?.asString,
                it["source"]?.asString,
                it["restartable"]?.asBoolean == true,
            )
        }

    fun scopes(scopes: List<ScopeState>) =
        JsonArray().apply {
            scopes.forEach { scope ->
                add(
                    JsonObject().apply {
                        addProperty("name", scope.name)
                        addProperty("reference", scope.variablesReference)
                    },
                )
            }
        }

    fun scopesOf(json: JsonArray): List<ScopeState> =
        json
            .map {
                it.asJsonObject
            }.map { ScopeState(it["name"].asString, it["reference"].asInt) }

    fun variables(variables: List<VariableState>) = JsonArray().apply { variables.forEach { add(variable(it)) } }

    fun variable(variable: VariableState) =
        JsonObject().apply {
            addProperty("name", variable.name)
            addProperty("value", variable.value)
            addProperty("type", variable.type)
            addProperty("reference", variable.variablesReference)
            addProperty("lazy", variable.lazy)
            addProperty("elements", variable.elements)
        }

    fun variablesOf(json: JsonArray): List<VariableState> = json.map { variableOf(it.asJsonObject) }

    fun variableOf(json: JsonObject) =
        VariableState(
            json["name"].asString,
            json["value"].asString,
            json["type"].asString,
            json["reference"].asInt,
            json["lazy"].asBoolean,
            json["elements"].asInt,
        )

    fun completions(completions: CompletionsState) =
        JsonObject().apply {
            addProperty("start", completions.start)
            addProperty("length", completions.length)
            add(
                "items",
                JsonArray().apply {
                    completions.items.forEach { item ->
                        add(
                            JsonObject().apply {
                                addProperty("label", item.label)
                                addProperty("kind", item.kind)
                            },
                        )
                    }
                },
            )
        }

    fun completionsOf(json: JsonObject) =
        CompletionsState(
            json["start"].asInt,
            json["length"].asInt,
            json.getAsJsonArray("items").map { it.asJsonObject }.map { CompletionState(it["label"].asString, it["kind"].asString) },
        )

    fun exceptions(stops: ExceptionStops) =
        JsonObject().apply {
            addProperty("all", stops.all)
            addProperty("uncaught", stops.uncaught)
            add("allTypes", JsonArray().apply { stops.allTypes.forEach(::add) })
            add("uncaughtTypes", JsonArray().apply { stops.uncaughtTypes.forEach(::add) })
        }

    /** The exception stops of a command, none when it has none. */
    fun exceptionsOf(json: JsonElement?): ExceptionStops =
        json?.takeIf { it.isJsonObject }?.asJsonObject?.let {
            ExceptionStops(
                all = it["all"]?.asBoolean == true,
                uncaught = it["uncaught"]?.asBoolean == true,
                allTypes = it.getAsJsonArray("allTypes")?.map { type -> type.asString }.orEmpty(),
                uncaughtTypes = it.getAsJsonArray("uncaughtTypes")?.map { type -> type.asString }.orEmpty(),
            )
        } ?: ExceptionStops()

    fun exceptionInfo(info: ExceptionInfoState) =
        JsonObject().apply {
            addProperty("type", info.type)
            info.text?.let { addProperty("text", it) }
            info.stackTrace?.let { addProperty("stackTrace", it) }
            addProperty("uncaught", info.uncaught)
        }

    fun exceptionInfoOf(json: JsonObject) =
        ExceptionInfoState(json["type"].asString, json["text"]?.asString, json["stackTrace"]?.asString, json["uncaught"].asBoolean)

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
