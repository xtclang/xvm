package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal fun ParityScenarios.graphCases() {
    case("X59") { data ->
        val document = graph()
        val result = graphReferences(document)
        check(result.size == data.int("referenceCount"))
        check(
            result.map { it.string("uri") }.sorted() ==
                data
                    .strings("modules")
                    .map { uri(SharedScenarios.text(data.string("moduleFile"), it)) }
                    .sorted(),
        )
        check(
            protocol.server().getOpenedDocuments().none {
                it.getFile().getPath() ==
                    directory.resolve(data.string("closedConsumer")).toString()
            },
        )
    }
    case("X60") { data ->
        val document = graph()
        val proposed =
            proposedRename(document, document.at(data.string("anchor")), data.string("replaceWith"))
                .asJsonObject
        check(
            proposed["changes"].let { it == null || it.isJsonNull || it.asJsonObject.size() == 0 },
        ) {
            proposed.toString()
        }
        val changes = proposed["documentChanges"].rows()
        check(changes.size == data.int("moduleCount"))
        val currentVersion = version(document)
        changes.forEach { change ->
            val target = change["textDocument"].asJsonObject
            if (target.string("uri") == document.uri) {
                check(target.int("version") == currentVersion)
            } else {
                check(target["version"] == null || target["version"].isJsonNull)
            }
        }
        applyRename(document, document.at(data.string("anchor")), data.string("replaceWith"))
        data.strings("modules").forEach { name ->
            val file = SharedScenarios.text(data.string("file"), name)
            val expected = fixture(file).replace(data.pattern("replaceFrom"), data.string("replaceWith"))
            // UP21 persists closed consumers for the compiler; the open editor stays unsaved.
            val expectedDisk = if (file == document.file) fixture(file) else expected
            check(Files.readString(directory.resolve(file)) == expectedDisk) {
                "Rename must preserve the open buffer's disk text and persist closed consumers: $file"
            }
            val changed = open(file)
            clean(changed)
            check(changed.text == expected)
        }
    }
    case("X61") { data ->
        val document = graph()
        check(
            proposedRename(document, document.at(data.string("anchor")), data.string("newName"))
                .isJsonNull,
        )
        check(document.text == fixture(data.string("file")))
        clean(document)
    }
    case("X62") { data ->
        graph()
        val document = open(data.string("file"))
        clean(document)
        val search = data.string("searchCall")
        with(driver) {
            signature(document.editor, document.at(search, search.length)) { signatures ->
                signatures.any {
                    data.pattern("searchName").containsMatchIn(it.label) &&
                        data.pattern("searchArgumentType").containsMatchIn(it.label)
                }
            }
            nativeHover(
                document,
                document.at(data.string("descriptionCall")),
                data.pattern("descriptionType"),
            )
        }
        check(proposedRename(document, document.at(search), data.string("searchRename")).isJsonNull)
        check(
            proposedRename(
                document,
                document.at(data.string("descriptionCall")),
                data.string("descriptionRename"),
            ).isJsonNull,
        )
    }
    case("X63") { data ->
        val document = graph()
        val consumer = open(data.string("file"))
        replace(
            consumer,
            fixture(consumer.file)
                .replace(data.string("replaceFrom"), data.string("addedReference")),
        )
        check(
            graphReferences(document).count { it.string("uri") == consumer.uri } ==
                data.int("consumerReferenceCount"),
        )
        val edit =
            proposedRename(document, document.at(data.string("anchor")), data.string("newName"))
                .asJsonObject
        val change =
            edit["documentChanges"].rows().single {
                it["textDocument"].asJsonObject.string("uri") == consumer.uri
            }
        check(change["textDocument"].asJsonObject.int("version") == version(consumer))
        replace(consumer, data.string("replaceWith"))
        errors(consumer)
        check(graphReferences(document).isEmpty())
        check(
            proposedRename(document, document.at(data.string("anchor")), data.string("newName"))
                .isJsonNull,
        )
        clean(document)
        replace(consumer, fixture(consumer.file))
        clean(consumer)
        check(graphReferences(document).size == data.int("referenceCount"))
    }
}

internal fun ParityScenarios.indexingCases() {
    case("X251") { data ->
        val roots =
            (0 until data.int("roots")).map { index ->
                val file = SharedScenarios.text(data.string("rootFile"), index.toString())
                write(file, SharedScenarios.text(data.string("rootSource"), index.toString()))
                SharedScenarios.SourceModule(SharedScenarios.text(data.string("rootName"), index.toString()), uri(file), emptyList())
            }
        val consumer = data["consumer"].asJsonObject
        write(consumer.string("uri"), consumer.string("source"))
        val graph =
            roots + SharedScenarios.SourceModule(consumer.string("name"), uri(consumer.string("uri")), consumer.strings("dependencies"))
        configure(graph)
        val document = open(data.string("file"))
        clean(document)

        fun refs() =
            query(
                "textDocument/references",
                document,
                document.at(data.string("anchor")),
                mapOf("context" to mapOf("includeDeclaration" to true)),
            ).rows()

        fun search(value: String) = protocol.query("workspace/symbol", mapOf("query" to value)).rows()
        check(refs().size == data.int("referenceCount"))
        check(
            protocol.server().getOpenedDocuments().none { it.getFile().getPath() == directory.resolve(consumer.string("uri")).toString() },
        )
        check(search(data.string("symbol")).size == data.int("roots"))
        check(search(data.string("symbol")).size == data.int("roots"))
        replace(document, document.text.replace(data.string("replaceFrom"), data.string("replacement")))
        clean(document)
        check(search(data.string("addedSymbol")).size == 1)
        check(refs().size == data.int("referenceCount"))
        val last = data.int("roots") - 1
        val broken = open(SharedScenarios.text(data.string("rootFile"), last.toString()))
        replace(broken, SharedScenarios.text(data.string("brokenSource"), last.toString()))
        errors(broken)
        check(refs().isEmpty())
        check(search(data.string("addedSymbol")).size == 1)
        replace(broken, SharedScenarios.text(data.string("rootSource"), last.toString()))
        clean(broken)
        check(refs().size == data.int("referenceCount"))
        configure(graph.filter { it.name != roots.last().name })
        with(driver) { awaitUi("removed root leaves index") { search(data.string("symbol")).size == data.int("roots") - 1 } }
        configure(graph)
        with(driver) { awaitUi("restored root returns to index") { search(data.string("symbol")).size == data.int("roots") } }
    }
}

private fun ParityWorkspace.graph(): ParityWorkspace.Document {
    val setup = common["graph"].asJsonObject
    setup["sourceModules"].rows().forEach { write(it.string("uri")) }
    configure(setup["sourceModules"])
    return open(setup.string("root")).also(::clean)
}

private fun ParityWorkspace.graphReferences(document: ParityWorkspace.Document): List<JsonObject> =
    query(
        "textDocument/references",
        document,
        document.at(common["graph"].asJsonObject.string("referenceAnchor")),
        mapOf("context" to mapOf("includeDeclaration" to true)),
    ).rows()

internal fun ParityScenarios.monikerCases() {
    case("X252") { data ->
        write(data.string("libraryFile"), data.string("librarySource"))
        write(data.string("consumerFile"), data.string("consumerSource"))
        configure(data["sourceModules"])
        val consumer = open(data.string("consumerFile"))
        clean(consumer)

        fun imports() = query("textDocument/moniker", consumer, consumer.at(data.string("reference"))).rows()
        val imported = imports().single()
        val text = data.string("librarySource")
        val offset = text.indexOf(data.string("declaration"))
        check(offset >= 0)
        val exported =
            protocol
                .query(
                    "textDocument/moniker",
                    mapOf(
                        "textDocument" to mapOf("uri" to uri(data.string("libraryFile"))),
                        "position" to
                            mapOf(
                                "line" to text.take(offset).count { it == '\n' },
                                "character" to offset - text.lastIndexOf('\n', offset) - 1,
                            ),
                    ),
                ).rows()
                .single()
        check(imported.string("scheme") == data.string("scheme"))
        check(imported.string("unique") == data.string("unique"))
        check(exported.string("kind") == "export")
        check(imported == exported.deepCopy().apply { addProperty("kind", "import") })
        val overload = query("textDocument/moniker", consumer, consumer.at(data.string("overload"))).rows().single()
        check(overload.string("identifier") != imported.string("identifier"))
        val library = open(data.string("libraryFile"))
        clean(library)

        fun exports() = query("textDocument/moniker", library, library.at(data.string("declaration"))).rows().single()
        check(exports() == exported)
        replace(library, text.replace(data.string("replaceFrom"), data.string("replaceWith")))
        clean(consumer)
        val changed = exports()
        check(changed.string("identifier") != exported.string("identifier"))
        check(imports().single() == changed.deepCopy().apply { addProperty("kind", "import") })
        replace(library, text)
        clean(consumer)
        check(imports().single() == imported)
    }
    case("X253") { data ->
        write(data.string("file"), data.string("source"))
        val document = open(data.string("file"))
        clean(document)

        fun monikers(key: String) = query("textDocument/moniker", document, document.at(data.string(key))).rows()
        val own = monikers("private").single()
        val bundled = monikers("library").single()
        check(own.string("kind") == "local")
        check(bundled.string("kind") == "import")
        check(bundled.string("scheme") == data.string("scheme"))
        check(bundled.string("unique") == data.string("unique"))
        check(monikers("local").isEmpty())
        replace(document, data.string("source").replace(data.string("replaceFrom"), data.string("replaceWith")))
        errors(document)
        check(monikers("private").isEmpty())
        replace(document, data.string("source"))
        clean(document)
        check(monikers("private").single() == own)
        check(monikers("library").single() == bundled)
    }
}

internal fun ParityScenarios.libraryContentCases() {
    case("X254") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        data.strings("types").forEach { name ->
            val at = document.at(name)
            val imported = query("textDocument/moniker", document, at).rows().single()
            val target = query("textDocument/definition", document, at).rows().single()
            val uri = target.string("uri")
            // TODO LSP4IJ: UP25 — retain native read-only file acceptance until upstream supports
            // workspace/textDocumentContent, virtual URI resolution and refresh.
            check(URI(uri).scheme == "file")
            val path = Path.of(URI(uri))
            check(!Files.isWritable(path))
            val library = open(path.toString())
            val text = library.text
            val start = target["range"].asJsonObject["start"].asJsonObject
            check(text.lines()[start.int("line")].substring(start.int("character")).startsWith(name))
            val exported =
                protocol
                    .query(
                        "textDocument/moniker",
                        mapOf(
                            "textDocument" to mapOf("uri" to uri),
                            "position" to start,
                        ),
                    ).rows()
                    .single()
            check(exported == imported.deepCopy().apply { addProperty("kind", "export") })
            check(exported.string("scheme") == data.string("monikerScheme"))
            check(
                query(
                    "textDocument/formatting",
                    library,
                    extra = mapOf("options" to mapOf("tabSize" to 4, "insertSpaces" to true)),
                ).rows().isEmpty(),
            )
            check(library.text == text && Files.readString(path) == text)
        }
    }
}
