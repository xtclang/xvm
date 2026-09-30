package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeGlobalBackendAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.platformCases() {
    case("X135") { data ->
        write(data.string("file"), data.string("source"))
        val document = open(data.string("file"))
        clean(document)
        with(driver) {
            withContext(OnDispatcher.EDT) { getToolWindow("Language Servers").hide() }
            listOf(true, false, true, false).forEach { visible ->
                invokeAction("xtc.toggleServerLog")
                awaitUi("Ecstasy server log visibility = $visible", 15.seconds) {
                    withContext(OnDispatcher.EDT) {
                        getToolWindow("Language Servers").isVisible()
                    } == visible
                }
            }
        }
        check(document.text == data.string("source"))
    }

    case("X124") { data ->
        val external = Files.createTempDirectory("xtc-playbook-resources-").toRealPath()
        try {
            write(data.string("file"), data.string("text"))
            val resourceRoot = external.resolve(data.string("resourceDirectory"))
            val roots = listOf(resourceRoot.toUri().toString())
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
            Files.createDirectories(resourceRoot)
            val resource = resourceRoot.resolve(data.string("resource"))
            Files.writeString(resource, data.string("contents"))
            clean(document)
            Files.delete(resource)
            errors(document)
            Files.writeString(resource, data.string("contents"))
            clean(document)
            val replacement = external.resolve("replacement")
            configure(
                listOf(
                    SharedScenarios.SourceModule(
                        data.string("module"),
                        uri(data.string("file")),
                        emptyList(),
                        listOf(replacement.toUri().toString()),
                    )
                )
            )
            errors(document)
            Files.createDirectories(replacement)
            Files.writeString(replacement.resolve(data.string("resource")), data.string("contents"))
            clean(document)
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(CompilerSettingsPage::class).clearProjectGraph(singleProject())
                }
            }
            Files.walk(external).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
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
    case("X130") { data ->
        data["files"].rows().forEach { write(it.string("file"), it.string("text")) }
        val target = Files.createDirectories(directory.resolve(data.string("destination")))
        refresh(target)
        // A focused run has no earlier source editor to start the installed language client.
        val document = open(data.string("consumer"))
        protocol.server()
        val root = with(driver) { Path.of(singleProject().getBasePath()) }
        with(driver) { changeWorkspaceFolders(root, directory) }
        configure(null)
        try {
            clean(document)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(FileTreeOperations::class)
                        .move(
                            singleProject(),
                            data.strings("sources").map { directory.resolve(it).toString() },
                            target.toString(),
                        )
                }
                val dialog = ui.dialog(title = "Move Ecstasy Sources")
                awaitUi("native Move dialog", 45.seconds) { dialog.present() }
                withContext(OnDispatcher.EDT) {
                    cast(dialog.button("Refactor").component, NativeButton::class).doClick()
                }
                fun moved(expected: Boolean) =
                    data.strings("sources").all {
                        Files.exists(target.resolve(it)) == expected &&
                            Files.exists(directory.resolve(it)) != expected
                    }
                awaitUi("all selected containers moved", 45.seconds) { moved(true) }
                clean(document)
                listOf("\$Undo" to false, "\$Redo" to true).forEach { (action, expected) ->
                    // A file-only Move belongs to project history, not the unchanged Consumer
                    // editor. Check availability before dispatch instead of timing out on a no-op.
                    awaitUi("project $action is available") {
                        withContext(OnDispatcher.EDT) {
                            utility(FileTreeOperations::class)
                                .globalHistoryAvailable(singleProject(), expected)
                        }
                    }
                    invokeGlobalBackendAction(action, project = singleProject(), now = false)
                    awaitUi("one $action restores all container paths", 45.seconds) {
                        val confirm = ui.dialog(title = if (expected) "Redo" else "Undo")
                        if (confirm.present())
                            withContext(OnDispatcher.EDT) {
                                cast(
                                        confirm.button(if (expected) "Redo" else "Undo").component,
                                        NativeButton::class,
                                    )
                                    .doClick()
                            }
                        moved(expected)
                    }
                    clean(document)
                }
            }
            data["files"].rows().forEach { file ->
                val original = file.string("file")
                val moved =
                    if (data.strings("sources").any { original.startsWith("$it/") })
                        "${data.string("destination")}/$original"
                    else original
                check(Files.readString(directory.resolve(moved)) == file.string("text"))
            }
        } finally {
            configure(emptyList())
            with(driver) { changeWorkspaceFolders(directory, root) }
        }
    }
    case("X131") { data ->
        write(data.string("file"), data.string("source"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("module"),
                    uri(data.string("file")),
                    emptyList(),
                )
            )
        )
        val document = open(data.string("file"))
        clean(document)
        val lens = query("textDocument/codeLens", document).rows().single()
        val resolvedLens =
            if (lens.has("data")) protocol.query("codeLens/resolve", lens).asJsonObject else lens
        check(resolvedLens["range"] == lens["range"])
        check(resolvedLens["command"].asJsonObject["arguments"].asJsonArray.size() == 2)
        val link = query("textDocument/documentLink", document).rows().single()
        val resolvedLink =
            if (link.has("data")) protocol.query("documentLink/resolve", link).asJsonObject
            else link
        check(resolvedLink.string("target") == data.string("link"))
        val hints =
            query(
                    "textDocument/inlayHint",
                    document,
                    extra =
                        mapOf(
                            "range" to
                                mapOf(
                                    "start" to mapOf("line" to 0, "character" to 0),
                                    "end" to mapOf("line" to 6, "character" to 0),
                                )
                        ),
                )
                .rows()
        val hint = hints.first { it.has("data") || it.has("tooltip") }
        val resolvedHint =
            if (hint.has("data")) protocol.query("inlayHint/resolve", hint).asJsonObject else hint
        check(resolvedHint["label"] == hint["label"] && resolvedHint.has("tooltip"))
        val symbol =
            protocol
                .query("workspace/symbol", mapOf("query" to data.string("module")))
                .rows()
                .first()
        val resolvedSymbol =
            if (symbol.has("data")) protocol.query("workspaceSymbol/resolve", symbol).asJsonObject
            else symbol
        check(resolvedSymbol["location"].asJsonObject.has("range"))
        replace(document, "\n" + data.string("source"))
        listOf(
                "codeLens/resolve" to lens,
                "documentLink/resolve" to link,
                "inlayHint/resolve" to hint,
                "workspaceSymbol/resolve" to symbol,
            )
            .filter { it.second.has("data") }
            .forEach { (method, item) ->
                check(
                    runCatching { protocol.query(method, item) }
                        .exceptionOrNull()
                        ?.message
                        ?.contains("expired or changed") == true
                )
            }
    }
    case("X132") { data ->
        write(data.string("file"), data.string("source"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("module"),
                    uri(data.string("file")),
                    emptyList(),
                )
            )
        )
        val document = open(data.string("file"))
        clean(document)
        val edits =
            query(
                    "textDocument/rangesFormatting",
                    document,
                    extra =
                        mapOf(
                            "options" to mapOf("tabSize" to 4, "insertSpaces" to true),
                            "ranges" to data["ranges"],
                        ),
                )
                .rows()
        check(
            edits.map { it["range"].asJsonObject["start"].asJsonObject["line"].asInt } ==
                data["lines"].asJsonArray.map { it.asInt }
        )
        check(edits.all { it.string("newText") == data.string("indent") })
        val sync = protocol.capabilities().asJsonObject["textDocumentSync"]
        if (sync.isJsonObject) {
            val save =
                mapOf("textDocument" to mapOf("uri" to uri(data.string("file"))), "reason" to 1)
            if (sync.asJsonObject["willSave"]?.asBoolean == true)
                protocol.notify("textDocument/willSave", save)
            if (sync.asJsonObject["willSaveWaitUntil"]?.asBoolean == true)
                check(protocol.query("textDocument/willSaveWaitUntil", save).rows().isEmpty())
        }
        check(document.text == data.string("source"))
    }
    case("X133") { data ->
        write(data.string("file"), data.string("source"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("module"),
                    uri(data.string("file")),
                    emptyList(),
                )
            )
        )
        val document = open(data.string("file"))
        clean(document)
        fun linked(anchor: String) =
            query("textDocument/linkedEditingRange", document, document.text.indexOf(anchor))
                .asJsonObject
        fun ranges(anchor: String) =
            linked(anchor)["ranges"]?.takeUnless { it.isJsonNull }?.asJsonArray
        check(
            ranges(data.string("anchor"))!!.map {
                it.asJsonObject["start"].asJsonObject["character"].asInt
            } == data.strings("uses").map { data.string("source").indexOf(it) }
        )
        data.strings("refused").forEach { check(ranges(it)?.size() in listOf(null, 0)) }
        replace(document, data.string("broken"))
        errors(document)
        check(ranges(data.string("anchor"))?.size() in listOf(null, 0))
        replace(document, data.string("source"))
        clean(document)
        check(ranges(data.string("anchor"))?.size() == 2)
    }
    case("X134") { data ->
        val external = Files.createTempDirectory("xtc-playbook-source-").toRealPath()
        try {
            val library = external.resolve(data.string("libraryFile"))
            Files.writeString(library, data.string("library"))
            write(data.string("file"), data.string("consumer"))
            configure(
                listOf(
                    SharedScenarios.SourceModule(
                        data.string("libraryModule"),
                        library.toUri().toString(),
                        emptyList(),
                    ),
                    SharedScenarios.SourceModule(
                        data.string("module"),
                        uri(data.string("file")),
                        listOf(data.string("libraryModule")),
                    ),
                )
            )
            val document = open(data.string("file"))
            clean(document)
            Files.writeString(library, data.string("brokenLibrary"))
            errors(document)
            Files.writeString(library, data.string("library"))
            clean(document)
            Files.delete(library)
            errors(document)
            Files.writeString(library, data.string("library"))
            clean(document)
        } finally {
            configure(emptyList())
            Files.walk(external).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.FileTreeOperations", plugin = "org.xtclang.playbook.probe")
interface FileTreeOperations {
    fun globalHistoryAvailable(project: Project, redo: Boolean): Boolean

    fun move(project: Project, paths: List<String>, destination: String)

    fun rename(project: Project, path: String)

    fun loadDirectory(path: String)
}
