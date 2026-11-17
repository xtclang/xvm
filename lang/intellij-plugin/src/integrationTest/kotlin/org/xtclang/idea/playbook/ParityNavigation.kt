package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

internal fun ParityScenarios.navigationCases() {
    case("X3") { data ->
        val doc = open(data.string("file"))
        clean(doc)
        val narrowed = doc.at(data.string("narrowUse"), data.int("offset"))
        val wide = doc.at(data.string("wideUse"), data.int("offset"))
        with(driver) {
            nativeHover(doc, narrowed, data.pattern("narrowedType"))
            nativeHover(doc, wide, data.pattern("declaredType"))
        }
        val first = targets(doc, "definition", narrowed)
        val second = targets(doc, "definition", wide)
        check(first.size == data.int("targetCount") && first == second)
        check(
            first.single().line() ==
                ParityWorkspace.position(
                    doc.text,
                    doc.at(data.string("declaration"), data.int("offset")),
                )["line"],
        )
    }
    case("X5") { data ->
        val doc = open(data.string("file"))
        check(
            names(targets(doc, "definition", doc.at(data.string("libraryType")))) ==
                listOf(data.string("libraryTarget")),
        )
        replace(doc, fixture(doc.file).substringBeforeLast(data.string("missingCloser")))
        diagnostics(doc) { errors ->
            errors.any { it.code?.startsWith(data.string("parserCodePrefix")) == true }
        }
        with(driver) {
            structure(doc.editor, listOf(data.string("retainedSymbol")))
            awaitUi("recovered folds", HANG_LIMIT) { folds(doc.editor).isNotEmpty() }
            selectionParents(doc.editor, doc.at(data.string("anchor"), data.int("offset")))
        }
        check(
            targets(doc, "definition", doc.at(data.string("anchor"), data.int("offset"))).isEmpty(),
        )
        replace(doc, fixture(doc.file))
        clean(doc)
    }
    case("X21") { data ->
        val root = project()
        val refs =
            query(
                "textDocument/references",
                root,
                root.at(data.string("anchor")),
                mapOf("context" to mapOf("includeDeclaration" to true)),
            ).rows()
        check(refs.any { it.string("uri") == uri(data.string("file")) })
        val symbols =
            protocol.query("workspace/symbol", mapOf("query" to data.string("symbolName"))).rows()
        check(
            symbols.any { it.getAsJsonObject("location").string("uri") == uri(data.string("file")) },
        )
        check(
            protocol.server().getOpenedDocuments().none {
                it.getFile().getPath() == directory.resolve(data.string("file")).toString()
            },
        )
        val child = open(data.string("file"))
        data.strings("uses").forEach { word ->
            val locations = targets(child, "definition", child.at(word))
            check(
                locations.size == data.int("targetCount") &&
                    locations.single().string("uri") == root.uri,
            )
        }
    }
    case("X22") { data ->
        val root = project()
        val base = hierarchy(root, root.at(data.string("baseDeclaration")))
        val children = edges(base, "subtypes")
        check(children.size == data.int("childCount"))
        check(children.single().string("name") == data.string("childName"))
        check(children.single().string("uri") == uri(data.string("file")))
        val parents = edges(children.single(), "supertypes")
        check(
            parents.any {
                "${it.string("name")} ${it["detail"]}".contains(data.string("typeArgument"))
            },
        ) {
            parents.toString()
        }
        check(edges(base, "supertypes").any { it.string("name") == data.string("interfaceName") })
        val named =
            hierarchy(root, root.at(data.string("interfaceDeclaration"), data.int("offset")))
        check(
            edges(named, "subtypes").any { it.string("name").startsWith(data.string("baseName")) },
        )
        with(driver) {
            nativeHierarchy(
                root,
                root.at(data.string("baseDeclaration")),
                "type",
                listOf(data.string("childName")),
            )
        }
    }
    case("X36") { data ->
        val doc = open(data.string("file"))
        check(
            names(targets(doc, "implementation", doc.at(data.string("anchor")))).sorted() ==
                data.strings("implementations").sorted(),
        )
    }
    case("X37") { data ->
        val doc = open(data.string("file"))
        val lines =
            data
                .pattern("overrides")
                .findAll(doc.text)
                .map {
                    ParityWorkspace
                        .position(
                            doc.text,
                            it.range.first + data.string("declarationPrefix").length,
                        ).getValue("line")
                }.toList()
        check(lines.size == data.int("declarationOffset"))
        listOf(
            doc.at(data.string("declaration"), data.int("declarationOffset")),
            doc.at(data.string("use"), data.int("useOffset")),
        ).forEach { at ->
            check(
                targets(doc, "implementation", at).map { it.line() }.sorted() == lines.sorted(),
            )
        }
        val offset =
            doc.text
                .lineSequence()
                .take(lines[1])
                .sumOf { it.length + 1 } +
                doc.text.lines()[lines[1]].indexOf(data.string("methodName"))
        val child = targets(doc, "implementation", offset)
        check(child.size == data.int("targetCount") && child.single().line() == lines[1])
    }
    case("X153") { data ->
        val variants = data["variants"].rows()
        write(data.string("file"), variants.first().string("source"))
        val doc = open(data.string("file"))
        variants.forEach { variant ->
            replace(doc, variant.string("source"))
            clean(doc)
            val actual = targets(doc, variant.string("kind"), doc.at(variant.string("anchor"), variant.int("offset")))
            val expected =
                variant
                    .strings("targets")
                    .map { anchor ->
                        ParityWorkspace.position(doc.text, doc.at(anchor))
                    }.toSet()
            check(
                actual.map { it.getAsJsonObject("range").getAsJsonObject("start") }.toSet() ==
                    expected.map { Gson().toJsonTree(it).asJsonObject }.toSet(),
            ) { "Unexpected implementation locations: $actual; expected $expected" }
            check(actual.all { it.string("uri") == doc.uri })
        }
    }
    case("X38") { data ->
        val root = project()
        replace(
            root,
            fixture(data.string("rootFile"))
                .replace(data.string("replaceFrom"), data.string("rootWithFactory")),
        )

        fun lookup() = targets(root, "typeDefinition", root.at(data.string("factory")))
        val original = lookup()
        check(
            original.size == data.int("targetCount") &&
                original.single().string("uri") == uri(data.string("memberFile")),
        )
        val implementations = targets(root, "implementation", root.at(data.string("method")))
        check(
            implementations.size == data.int("targetCount") &&
                implementations.single().string("uri") == root.uri,
        )
        val child = open(data.string("memberFile"))
        replace(child, "\n\n" + fixture(child.file))
        check(lookup().single().line() == original.single().line() + data.int("lineShift"))
        replace(child, data.string("replaceWith"))
        check(lookup().isEmpty())
        replace(child, fixture(child.file))
        check(lookup().single().line() == original.single().line())
    }
    case("X64") { data ->
        val doc = open(data.string("file"))
        val expected = data["locations"].rows().map { expectedRange(doc, it) }
        listOf(
            doc.at(data.string("anchor2"), data.int("offset2")),
            doc.at(data.string("anchor"), data.int("offset")),
        ).forEach { at ->
            assertRanges(targets(doc, "implementation", at).map { it["range"] }, expected)
        }
    }
    case("X65") { data ->
        val doc = open(data.string("file"))
        data["variants"].rows().forEach { variant ->
            val expected = variant["expected"].rows().map { expectedRange(doc, it) }
            assertRanges(
                targets(
                    doc,
                    "implementation",
                    doc.at(variant.string("anchor"), variant.int("offset")),
                ).map { it["range"] },
                expected,
            )
        }
    }
    case("X66") { data ->
        val doc = open(data.string("file"))
        data["variants"].rows().forEach { variant ->
            check(
                targets(
                    doc,
                    "implementation",
                    doc.at(variant.string("anchor"), variant.int("offset")),
                ).isEmpty(),
            )
        }
    }
    case("X67") { data ->
        write(data.string("memberFile"), data.string("original"))
        val root = open(data.string("rootFile"))

        fun lookup() = targets(root, "implementation", root.at(data.string("anchor"), 2))
        val original = lookup()
        check(original.size == data.int("targetCount"))
        val memberUri = uri(data.string("memberFile"))
        check(
            original.single { it.string("uri") == memberUri }.line() == data.int("declarationLine"),
        )
        val member = open(data.string("memberFile"))
        replace(member, "\n\n" + data.string("original"))
        check(lookup().single { it.string("uri") == memberUri }.line() == data.int("lineShift"))
        replace(member, data.string("replaceWith"))
        check(lookup().isEmpty())
        replace(member, data.string("original"))
        check(lookup().size == data.int("targetCount"))
        check(Files.readString(directory.resolve(member.file)) == data.string("original"))
    }
    case("X68") { data ->
        val doc = open(data.string("file"))
        data["variants"].rows().forEach { variant ->
            val targets =
                targets(doc, "implementation", doc.at(variant.string("use"), variant.int("offset")))
            check(targets.size == data.int("targetCount"))
            val start = doc.at(variant.string("declaration"), variant.int("declarationOffset"))
            check(targets.single()["range"] == sourceRange(doc, start, variant.int("length")))
        }
    }
    case("X69") { data ->
        val doc = open(data.string("file"))
        val targets = targets(doc, "definition", doc.at(data.string("anchor")))
        check(targets.size == data.int("targetCount"))
        val start = doc.at(data.string("inheritedDeclaration"), data.int("inheritedOffset"))
        check(targets.single()["range"] == sourceRange(doc, start, data.int("targetLength")))
        val caller =
            query(
                "textDocument/prepareCallHierarchy",
                doc,
                doc.at(data.string("override"), data.int("overrideOffset")),
            ).rows()
                .single()
        val calls = protocol.query("callHierarchy/outgoingCalls", mapOf("item" to caller)).rows()
        check(calls.any { it.getAsJsonObject("to")["selectionRange"] == targets.single()["range"] })
        with(driver) {
            rejectRename(doc.editor, doc.at(data.string("anchor")), data.string("renameRejection"))
        }
    }
    case("X72") { data ->
        val doc = open(data.string("file"))
        data["variants"].rows().forEach { variant ->
            val targets =
                targets(doc, "implementation", doc.at(variant.string("use"), data.int("offset")))
            val expected =
                listOf("getter", "setter").map { name ->
                    sourceRange(
                        doc,
                        doc.at(variant.string(name), variant.int("${name}Offset")),
                        data.int("targetLength"),
                    )
                }
            check(targets.size == data.int("targetCount"))
            assertRanges(targets.map { it["range"] }, expected)
        }
    }
}

/**
 * X350: in a bundled library's source, which Go to Definition opened, Go to Definition on a call goes to the method
 * another source of the library declares, which only the library's whole compile resolves.
 */
internal fun ParityScenarios.libraryNavigationCases() {
    case("X350") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        // as a user opens it: Go to Definition on a library type opens the library's source, read-only, at its declaration
        val type = targets(document, "definition", document.at(data.string("libraryType"))).single()
        // TODO LSP4IJ: UP25 — LSP4IJ 0.21.0 has no workspace/textDocumentContent, so the library's source is the
        // server's protected copy on disk, not its ecstasy-library content
        check(URI(type.string("uri")).scheme == "file") { "The library source is not a file: $type" }
        val path = Path.of(URI(type.string("uri")))
        check(path.endsWith(data.string("librarySource")) && !Files.isWritable(path)) { "Not the read-only library source: $path" }
        check(Files.readAllLines(path)[type.line()].startsWith(data.string("libraryDeclaration"))) { "Not the type's declaration: $type" }
        val library = open(path, data.string("librarySource"))

        // a call in a method body to a method of another source of the library, which the copy compiled on its own,
        // with only its imports and type names, cannot resolve; the query waits for the library's compile
        val call = library.at(data.string("call"), data.int("callOffset"))
        val target = targets(library, "definition", call).single()
        val declared = Path.of(URI(target.string("uri")))
        check(declared.endsWith(data.string("targetSource")) && !Files.isWritable(declared)) {
            "Not the read-only library source: $declared"
        }
        check(names(listOf(target)) == listOf(data.string("target")))
        check(Files.readAllLines(declared)[target.line()].contains(data.string("targetDeclaration"))) {
            "Not the method's declaration: $target"
        }

        // hover, which also needs the whole compile: the copy on its own describes only its own declarations
        val hover = query("textDocument/hover", library, call)
        check(hover.toString().contains(data.string("hover"))) { "Hover of ${data.string("target")}: $hover" }
    }
}

private fun assertRanges(
    actual: List<JsonElement>,
    expected: List<JsonElement>,
) {
    // Gson's parsed numbers and integer primitives compare equal but can hash differently.
    // Compare ordered lists so exact ranges and duplicate counts do not depend on that hash.
    check(actual.sortedBy(JsonElement::toString) == expected.sortedBy(JsonElement::toString)) {
        "Expected $expected; received $actual"
    }
}

private fun JsonObject.line(): Int = getAsJsonObject("range").getAsJsonObject("start").int("line")

private fun expectedRange(
    doc: ParityWorkspace.Document,
    data: JsonObject,
) = sourceRange(doc, doc.at(data.string("anchor"), data.int("offset")), data.int("length"))

internal fun sourceRange(
    doc: ParityWorkspace.Document,
    at: Int,
    length: Int,
): JsonObject =
    Gson()
        .toJsonTree(
            mapOf(
                "start" to ParityWorkspace.position(doc.text, at),
                "end" to ParityWorkspace.position(doc.text, at + length),
            ),
        ).asJsonObject
