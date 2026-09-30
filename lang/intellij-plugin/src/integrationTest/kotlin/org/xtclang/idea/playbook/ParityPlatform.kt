package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
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
    case("X136") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        val before =
            protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
        with(driver) {
            withContext(OnDispatcher.EDT) {
                utility(LanguageServicePage::class).exercise(singleProject())
            }
        }
        val after =
            protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
        check(before["pid"] == after["pid"])
        val queue = after["compilerQueue"].asJsonObject
        check(queue["queueSize"].asInt == queue["queuedJobs"].asJsonArray.size())
        check(document.text == data.string("source"))
    }
    case("X137") { data ->
        val document = open(data.string("file"), data.string("source"))
        replace(document, data.string("edited"))
        val original = with(driver) { utility(LanguageServicePage::class).content(singleProject()) }
        var previous = protocol.server().getCurrentProcessId()
        try {
            listOf("incremental", "full").forEach { transport ->
                with(driver) {
                    withContext(OnDispatcher.EDT) {
                        utility(LanguageServicePage::class).transport(singleProject(), transport)
                    }
                    awaitUi("transport restart into $transport", 45.seconds) {
                        protocol.server().getCurrentProcessId()?.let { it != previous } == true
                    }
                }
                previous = protocol.server().getCurrentProcessId()
                val status =
                    protocol
                        .query("xtc/languageServiceStatus", emptyMap<String, Any>())
                        .asJsonObject
                check(status.string("textSynchronization") == transport)
                check(document.text == data.string("edited"))
                settle(document)
                clean(document)
            }
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).restore(singleProject(), original)
                }
            }
        }
    }
    case("X138") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        val previousPid = protocol.server().getCurrentProcessId()
        val original =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class)
                        .indent(singleProject(), data["indent"].asInt)
                }
            }
        try {
            with(driver) {
                awaitUi("live Code Style configuration", 15.seconds) {
                    protocol
                        .query("xtc/languageServiceStatus", emptyMap<String, Any>())
                        .asJsonObject["formatting"]
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("indentSize")
                        ?.asInt == data["indent"].asInt
                }
            }
            fun formatted() =
                query(
                        "textDocument/formatting",
                        document,
                        extra = mapOf("options" to mapOf("tabSize" to 4, "insertSpaces" to true)),
                    )
                    .rows()
            check(formatted().any { it.string("newText") == data.string("expectedIndent") })
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).indent(singleProject(), 0)
                }
            }
            check(formatted().any { it.string("newText") == data.string("expectedIndent") })
            val status =
                protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
            check(
                !status["saveHookSupported"].asBoolean && !status["serverSaveFormatting"].asBoolean
            )
            check(protocol.server().getCurrentProcessId() == previousPid)
            check(document.text == data.string("source"))
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).indent(singleProject(), original)
                }
            }
        }
    }

    case("X139") { data ->
        val closed = open(data.string("closedFile"), data.string("closedSource"))
        replace(closed, data.string("closedSource") + "\n")
        with(driver) {
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject())
                    .closeFile(closed.editor.editor.getVirtualFile())
            }
        }
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        val previous =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).saveFormatting(singleProject(), true)
                }
            }
        try {
            replace(document, data.string("source") + "\n")
            with(driver) {
                // A bare FileDocumentManager.saveDocument bypasses Actions on Save.
                invokeAction("SaveAll", component = document.editor.component)
                awaitUi("native format on save", 30.seconds) {
                    document.text.contains("    Int value = 1;")
                }
                awaitUi("native format on save for a closed dirty tab", 30.seconds) {
                    closed.text == data.string("closedFormatted") + "\n"
                }
            }
            val status =
                protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
            check(!status["serverSaveFormatting"].asBoolean)
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).saveFormatting(singleProject(), previous)
                }
            }
        }
    }

    case("X144") { data ->
        write(data.string("file"), data.string("source"))
        write(data.string("otherFile"), data.string("otherSource"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("module"),
                    uri(data.string("file")),
                    emptyList(),
                ),
                SharedScenarios.SourceModule(
                    data.string("otherModule"),
                    uri(data.string("otherFile")),
                    emptyList(),
                ),
            )
        )
        val other = open(data.string("otherFile"))
        val document = open(data.string("file"))
        fun change(target: ParityWorkspace.Document, version: Int, text: String) =
            mapOf(
                "textDocument" to mapOf("uri" to target.uri, "version" to version),
                "edits" to
                    listOf(
                        mapOf(
                            "range" to
                                mapOf(
                                    "start" to ParityWorkspace.position(target.text, 0),
                                    "end" to
                                        ParityWorkspace.position(target.text, target.text.length),
                                ),
                            "newText" to text,
                        )
                    ),
            )
        fun apply(changes: List<Map<String, Any>>): JsonObject {
            val pending =
                with(driver) {
                    utility(WorkspaceEdits::class)
                        .apply(
                            singleProject(),
                            Gson()
                                .toJson(
                                    mapOf(
                                        "label" to "Ecstasy guarded edit",
                                        "edit" to mapOf("documentChanges" to changes),
                                    )
                                ),
                        )
                }
            return protocol.await("workspace/applyEdit", pending).asJsonObject
        }
        check(
            apply(listOf(change(document, version(document), data.string("changed"))))["applied"]
                .asBoolean
        )
        check(document.text == data.string("changed"))
        with(driver) {
            focusEditor(document.editor)
            invokeAction("\$Undo", now = false, component = document.editor.component)
            awaitUi("server edit Undo restores the source", 15.seconds) {
                document.text == data.string("source")
            }
        }
        val oldVersion = version(other)
        replace(other, data.string("otherChanged"))
        check(
            !apply(
                    listOf(
                        change(document, version(document), data.string("changed")),
                        change(other, oldVersion, data.string("otherSource")),
                    )
                )["applied"]
                .asBoolean
        )
        check(document.text == data.string("source"))
        check(other.text == data.string("otherChanged"))
        clean(document)
        clean(other)
    }

    case("X143") { data ->
        val source =
            "module ${data.string("module")} {\n" +
                (0 until data["declarations"].asInt).joinToString("\n") {
                    "    class ${data.string("prefix")}$it {}"
                } +
                "\n}\n"
        write(data.string("file"), source)
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
        query("textDocument/documentSymbol", document)
        val normal =
            protocol.query("workspace/symbol", mapOf("query" to data.string("prefix"))).rows()
        val before = trace.notifications("$/progress").size
        val final =
            protocol
                .query(
                    "workspace/symbol",
                    mapOf(
                        "query" to data.string("prefix"),
                        "partialResultToken" to data.string("token"),
                    ),
                )
                .rows()
        val batches =
            trace
                .notifications("$/progress")
                .drop(before)
                .filter { it["token"]?.asString == data.string("token") }
                .map { it["value"].asJsonArray }
        check(final.isEmpty())
        check(normal.size == data["declarations"].asInt)
        check(batches.size > 1 && batches.all { it.size() <= data["batchSize"].asInt })
        check(
            batches.flatMap { it.map { row -> row.asJsonObject.string("name") } } ==
                normal.map { it.string("name") }
        )
    }

    case("X142") { data ->
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
        val offset = document.text.indexOf(data.string("anchor"))
        check(document.text == data.string("source")) {
            "Initializer fixture changed before rename: ${Gson().toJson(document.text)}"
        }
        check(
            query("textDocument/hover", document, offset)
                .toString()
                .contains(data.string("expected"))
        )
        val definitions = query("textDocument/definition", document, offset).rows()
        check(
            definitions.any {
                it["range"].asJsonObject["start"].asJsonObject["character"].asInt ==
                    document.text.indexOf(data.string("name"))
            }
        )
        check(
            query(
                    "textDocument/references",
                    document,
                    offset,
                    mapOf("context" to mapOf("includeDeclaration" to true)),
                )
                .rows()
                .size == data["references"].asInt
        )
        applyRename(document, offset, data.string("newName"))
        check(
            document.text ==
                data.string("source").replace(data.string("name"), data.string("newName"))
        ) {
            "Initializer rename result: ${Gson().toJson(document.text)}"
        }
        with(driver) {
            invokeAction("\$Undo", now = false, component = document.editor.component)
            awaitUi("initializer rename Undo restores every occurrence", 15.seconds) {
                document.text == data.string("source")
            }
        }
        settle(document)
        clean(document)
    }

    case("X140") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        check(protocol.capabilities().asJsonObject.string("positionEncoding") == "utf-16")
        val offset = document.text.lastIndexOf(data.string("anchor"))
        check(
            query("textDocument/hover", document, offset)
                .toString()
                .contains(data.string("expected"))
        )
        val prepared =
            query("textDocument/prepareRename", document, offset).asJsonObject["range"].asJsonObject
        check(prepared["start"].asJsonObject["character"].asInt == offset)
        check(
            prepared["end"].asJsonObject["character"].asInt == offset + data.string("anchor").length
        )
    }
    case("X141") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        try {
            data["levels"]
                .asJsonArray
                .map { it.asString }
                .forEach { level ->
                    protocol.notify("$/setTrace", mapOf("value" to level))
                    val before = trace.notifications("$/logTrace").size
                    query(
                        "textDocument/hover",
                        document,
                        document.text.indexOf(data.string("anchor")),
                    )
                    with(driver) {
                        awaitUi("server runtime trace $level", 15.seconds) {
                            trace.notifications("$/logTrace").drop(before).any {
                                it.string("message").startsWith("textDocument/hover:") &&
                                    (it.has("verbose") == (level == "verbose")) &&
                                    !it.toString().contains(data.string("source").trim())
                            }
                        }
                    }
                }
        } finally {
            protocol.notify("$/setTrace", mapOf("value" to "verbose"))
        }
    }

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

@Remote("org.xtclang.idea.playbook.probe.WorkspaceEdits", plugin = "org.xtclang.playbook.probe")
interface WorkspaceEdits {
    fun apply(project: Project, json: String): ClientFuture
}

@Remote("org.xtclang.idea.playbook.probe.FileTreeOperations", plugin = "org.xtclang.playbook.probe")
interface FileTreeOperations {
    fun globalHistoryAvailable(project: Project, redo: Boolean): Boolean

    fun move(project: Project, paths: List<String>, destination: String)

    fun rename(project: Project, path: String)

    fun loadDirectory(path: String)
}
