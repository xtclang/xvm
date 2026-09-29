package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files

internal fun ParityScenarios.platformCases() {
    case("X124") { data ->
        val external = Files.createTempDirectory("xtc-playbook-resources-")
        try {
            write(data.string("file"), data.string("text"))
            val roots = listOf(external.toUri().toString())
            configure(
                listOf(
                    SharedScenarios.SourceModule(
                        data.string("module"),
                        uri(data.string("file")),
                        emptyList(),
                        roots,
                    )
                )
            )
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class)
                        .resourceRootsRoundTrip(singleProject(), Gson().toJson(roots))
                }
            }
            val document = open(data.string("file"))
            errors(document)
            val resource = external.resolve(data.string("resource"))
            Files.writeString(resource, data.string("contents"))
            clean(document)
            Files.delete(resource)
            errors(document)
            Files.writeString(resource, data.string("contents"))
            clean(document)
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class).clearProjectGraph(singleProject())
                }
            }
            Files.deleteIfExists(external.resolve(data.string("resource")))
            Files.deleteIfExists(external)
        }
    }
    case("X125") { data ->
        val document = open(data.string("file"), data.string("hoverSource"))
        clean(document)
        with(driver) {
            nativeHover(
                document,
                document.at(data.string("hoverAnchor")),
                Regex(Regex.escape(data.string("hoverExpected"))),
            )
        }
        data["signatures"].rows().forEach { call ->
            val marked = call.string("text")
            replace(document, marked.replace("§", ""))
            val help =
                query("textDocument/signatureHelp", document, marked.indexOf('§')).asJsonObject
            check(help["signatures"].rows().single()["parameters"].asJsonArray.size() == 2)
            check(help.int("activeParameter") == call.int("active"))
            with(driver) {
                signature(document.editor, marked.indexOf('§')) {
                    it.any { item -> "echo" in item.label }
                }
            }
        }
        data.strings("completions").forEach { marked ->
            replace(document, marked.replace("§", ""))
            with(driver) {
                lookup(document.editor, marked.indexOf('§')) { items ->
                    items.any { it.getLookupString() == data.string("completion") }
                }
                dismissPopups()
            }
        }
        replace(document, data.string("narrowedSource"))
        clean(document)
        check(
            targets(document, "typeDefinition", document.at(data.string("narrowedAnchor"))).any {
                it.string("uri").endsWith(data.string("targetSuffix"))
            }
        )
    }
    case("X126") { data ->
        val document = open(data.string("file"), data.string("source"))
        val full = query("textDocument/semanticTokens/full", document).asJsonObject
        check(full["data"].asJsonArray.size() > 0 && full.string("resultId").isNotEmpty())
        replace(document, "\n" + data.string("source"))
        val caps = protocol.capabilities().asJsonObject["semanticTokensProvider"].asJsonObject
        val deltaSupported =
            caps["full"].let { it.isJsonObject && it.asJsonObject["delta"]?.asBoolean == true }
        if (deltaSupported) {
            val delta =
                query(
                        "textDocument/semanticTokens/full/delta",
                        document,
                        extra = mapOf("previousResultId" to full.string("resultId")),
                    )
                    .asJsonObject
            val patched = full["data"].asJsonArray.map { it.asInt }.toMutableList()
            delta["edits"]
                .rows()
                .sortedByDescending { it.int("start") }
                .forEach { edit ->
                    val start = edit.int("start")
                    patched.subList(start, start + edit.int("deleteCount")).clear()
                    patched.addAll(start, edit["data"]?.asJsonArray?.map { it.asInt }.orEmpty())
                }
            check(
                patched ==
                    query("textDocument/semanticTokens/full", document)
                        .asJsonObject["data"]
                        .asJsonArray
                        .map { it.asInt }
            )
        } else {
            check(
                runCatching {
                        query(
                            "textDocument/semanticTokens/full/delta",
                            document,
                            extra = mapOf("previousResultId" to full.string("resultId")),
                        )
                    }
                    .exceptionOrNull()
                    ?.message
                    ?.contains("not negotiated") == true
            )
        }
        if (caps["range"]?.let { it.isJsonObject || it.asBoolean } == true) {
            check(
                query(
                        "textDocument/semanticTokens/range",
                        document,
                        extra =
                            mapOf(
                                "range" to
                                    mapOf(
                                        "start" to mapOf("line" to 0, "character" to 0),
                                        "end" to mapOf("line" to 1, "character" to 0),
                                    )
                            ),
                    )
                    .asJsonObject["data"]
                    .asJsonArray
                    .isEmpty
            )
        }
        discard(document)
        val reopened = open(data.string("file"))
        if (deltaSupported)
            check(
                query(
                        "textDocument/semanticTokens/full/delta",
                        reopened,
                        extra = mapOf("previousResultId" to full.string("resultId")),
                    )
                    .asJsonObject
                    .has("data")
            )
    }
    case("X127") { data ->
        val document = open(data.string("file"), data.string("source"))
        val action =
            query(
                    "textDocument/codeAction",
                    document,
                    extra =
                        mapOf(
                            "range" to
                                mapOf(
                                    "start" to mapOf("line" to 0, "character" to 0),
                                    "end" to
                                        mapOf("line" to 0, "character" to document.text.length),
                                ),
                            "context" to mapOf("diagnostics" to emptyList<Any>()),
                        ),
                )
                .rows()
                .single()
        val capability = protocol.capabilities().asJsonObject["codeActionProvider"]
        if (
            capability.isJsonObject && capability.asJsonObject["resolveProvider"]?.asBoolean == true
        ) {
            check(action.has("data") && !action.has("edit"))
            val resolved = protocol.query("codeAction/resolve", action).asJsonObject
            check(resolved["edit"].asJsonObject["documentChanges"].asJsonArray.size() == 1)
            check(resolved.string("title") == action.string("title"))
            replace(document, "\n" + data.string("source"))
            check(
                runCatching { protocol.query("codeAction/resolve", action) }
                    .exceptionOrNull()
                    ?.message
                    ?.contains("expired or changed") == true
            )
        } else check(action.has("edit"))
    }
}
