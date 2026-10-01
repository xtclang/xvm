package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import java.nio.file.Files

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
            val changed = open(file)
            clean(changed)
            check(
                changed.text ==
                    fixture(file).replace(data.pattern("replaceFrom"), data.string("replaceWith")),
            )
            check(Files.readString(directory.resolve(file)) == fixture(file)) {
                "Rename unexpectedly saved $file"
            }
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
