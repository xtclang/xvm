package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.platformCases() {
    case("X124") { data ->
        val external = Files.createTempDirectory("xtc-playbook-resources-").toRealPath()
        try {
            with(driver) { utility(FileTreeOperations::class).loadDirectory(external.toString()) }
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
            refresh(resource)
            clean(document)
            Files.delete(resource)
            refresh(external)
            errors(document)
            Files.writeString(resource, data.string("contents"))
            refresh(resource)
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
        write(data.string("file"), data.string("source"))
        configure(
            listOf(SharedScenarios.SourceModule("Resolve", uri(data.string("file")), emptyList()))
        )
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
    case("X128") { data ->
        data["variants"].rows().forEach { variant ->
            write(variant.string("root"), variant.string("source"))
            write(variant.string("member"), variant.string("memberSource"))
            configure(
                listOf(
                    SharedScenarios.SourceModule("App", uri(variant.string("root")), emptyList())
                )
            )
            val document = open(variant.string("root"))
            clean(document)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(FileTreeOperations::class)
                        .rename(
                            singleProject(),
                            directory.resolve(variant.string("from")).toString(),
                        )
                }
                chooseXtcFileRename()
                fillRenameDialog(variant.string("newName"))
                awaitUi("native file rename updates references", 45.seconds) {
                    document.text == variant.string("expected")
                }
            }
            check(open(variant.string("target")).text == variant.string("targetSource"))
            clean(document)
        }
    }

    case("X129") { data ->
        val root = with(driver) { Path.of(singleProject().getBasePath()) }
        val report = root.resolve(".gradle/xtc/lsp-model.json")
        val model =
            JsonParser.parseString(
                    data["model"]
                        .toString()
                        .replace("\${workspace}", directory.toUri().toString().trimEnd('/'))
                )
                .asJsonObject
        val inputs = model["sourceSets"].asJsonArray[0].asJsonObject
        val resources = inputs["resourceRoots"].deepCopy()
        fun refresh() =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class).refreshBuildModel(singleProject())
                }
            }
        fun writeModel() {
            Files.createDirectories(report.parent)
            Files.writeString(report, model.toString())
            refresh()
        }
        fun automatic() =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    check(
                        utility(CompilerSettingsPage::class)
                            .useBuildModel(singleProject())
                            .contains("Gradle model")
                    )
                }
            }
        try {
            write(data.string("file"), data.string("source"))
            write(data.string("resource"), data.string("contents"))
            writeModel()
            automatic()
            val document = open(data.string("file"))
            clean(document)
            inputs.add("resourceRoots", Gson().toJsonTree(emptyList<String>()))
            writeModel()
            errors(document)
            inputs.add("resourceRoots", resources)
            writeModel()
            clean(document)
            Files.writeString(report, "{")
            refresh()
            clean(document)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class).clearProjectGraph(singleProject())
                }
            }
            configure(
                listOf(
                    SharedScenarios.SourceModule(
                        "GradleAssets",
                        document.uri,
                        emptyList(),
                        emptyList(),
                    )
                )
            )
            errors(document)
            writeModel()
            errors(document)
            automatic()
            clean(document)
        } finally {
            Files.deleteIfExists(report)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class).clearProjectGraph(singleProject())
                }
            }
            refresh()
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.FileTreeOperations", plugin = "org.xtclang.playbook.probe")
interface FileTreeOperations {
    fun rename(project: Project, path: String)

    fun loadDirectory(path: String)
}
