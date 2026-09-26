package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

/** Protocol assertions use the installed client's existing connection, never a second server. */
class ClientProtocol(
    private val driver: Driver,
) {
    private val gson = Gson()

    fun server(): StartedLanguageServer =
        with(driver) {
            waitFor(
                message = "installed language client is started",
                timeout = 45.seconds,
                getter = { service<LanguageClients>(singleProject()).getStartedServers().toList() },
                checker = { it.size == 1 },
            ).single()
        }

    fun capabilities(): JsonElement = copy(server().getServerCapabilitiesSync())

    fun request(
        method: String,
        params: Any,
    ): ClientFuture =
        with(driver) {
            cast(server().getLanguageServer(), ClientEndpoint::class).request(method, remoteJson(params))
        }

    fun notify(
        method: String,
        params: Any,
    ) = with(driver) { cast(server().getLanguageServer(), ClientEndpoint::class).notify(method, remoteJson(params)) }

    fun query(
        method: String,
        params: Any,
    ): JsonElement = await(method, request(method, params))

    fun await(
        method: String,
        future: ClientFuture,
    ): JsonElement =
        with(driver) {
            waitFor("installed client response: $method", 60.seconds) { future.isDone() }
            if (method in listResults) {
                cast(future, ClientListFuture::class).get()?.let { values ->
                    JsonParser.parseString("[${values.joinToString(",") { copy(it).toString() }}]")
                } ?: JsonNull.INSTANCE
            } else {
                copy(cast(future, ClientObjectFuture::class).get())
            }
        }

    fun copy(value: ClientValue?): JsonElement =
        with(driver) {
            if (value == null) {
                JsonNull.INSTANCE
            } else {
                JsonParser.parseString(utility(ClientJsonTools::class).getLsp4jGson().toJson(value))
            }
        }

    private fun remoteJson(value: Any): ClientJson =
        with(driver) { utility(ClientJsonParser::class).parseString(if (value is JsonElement) value.toString() else gson.toJson(value)) }

    private companion object {
        // Driver transports Java collection returns as reference lists; object/Either returns are
        // individual references. Preserve that distinction before copying through LSP4IJ's Gson.
        val listResults =
            setOf(
                "textDocument/references",
                "textDocument/documentSymbol",
                "textDocument/documentHighlight",
                "textDocument/foldingRange",
                "textDocument/selectionRange",
                "textDocument/inlayHint",
                "textDocument/formatting",
                "textDocument/prepareTypeHierarchy",
                "typeHierarchy/supertypes",
                "typeHierarchy/subtypes",
                "textDocument/prepareCallHierarchy",
                "callHierarchy/incomingCalls",
                "callHierarchy/outgoingCalls",
            )
    }
}

@Remote("org.eclipse.lsp4j.jsonrpc.Endpoint", plugin = "com.redhat.devtools.lsp4ij")
interface ClientEndpoint {
    fun request(
        method: String,
        params: ClientJson,
    ): ClientFuture

    fun notify(
        method: String,
        params: ClientJson,
    )
}

@Remote("java.util.concurrent.CompletableFuture")
interface ClientFuture {
    fun isDone(): Boolean

    fun isCancelled(): Boolean

    fun isCompletedExceptionally(): Boolean

    fun cancel(interrupt: Boolean): Boolean
}

@Remote("java.util.concurrent.CompletableFuture")
interface ClientObjectFuture : ClientFuture {
    fun get(): ClientValue?
}

@Remote("java.util.concurrent.CompletableFuture")
interface ClientListFuture : ClientFuture {
    fun get(): List<ClientValue>?
}

@Remote("java.lang.Object")
interface ClientValue

@Remote("com.google.gson.JsonElement", plugin = "com.redhat.devtools.lsp4ij")
interface ClientJson

@Remote("com.google.gson.JsonParser", plugin = "com.redhat.devtools.lsp4ij")
interface ClientJsonParser {
    fun parseString(json: String): ClientJson
}

@Remote("com.redhat.devtools.lsp4ij.JSONUtils", plugin = "com.redhat.devtools.lsp4ij")
interface ClientJsonTools {
    fun getLsp4jGson(): ClientGson
}

@Remote("com.google.gson.Gson", plugin = "com.redhat.devtools.lsp4ij")
interface ClientGson {
    fun toJson(value: ClientValue): String
}
