package org.xtclang.idea.playbook

import com.google.gson.JsonParser
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import org.xtclang.idea.playbook.probe.HANG_SECONDS
import java.nio.file.Files

internal fun ParityScenarios.moduleCases() {
    case("X14") { data ->
        val marked =
            fixture(data.string("file"))
                .replace(data.string("method"), data.string("incompleteMethod"))
        val disk = marked.replace("§", "").replace("\n", "\r\n")
        val document = open(data.string("file"), disk)
        // IntelliJ normalizes the in-memory document to LF. Its saved file must retain CRLF.
        val at = marked.indexOf('§')
        check(document.text == marked.replace("§", ""))
        val expected = document.text.replace(data.string("prefix"), data.string("accepted"))
        with(driver) { accept(document.editor, at, data.string("label"), expected) }
        clean(document)
        save(document)
        awaitUi("saved completion preserves emoji and CRLF", HANG_LIMIT) {
            Files.readString(directory.resolve(document.file)) == expected.replace("\n", "\r\n")
        }
    }
    case("X23") { data ->
        val root = project()
        val child = open(data.string("memberFile"))
        val version = version(child)
        replace(
            root,
            fixture(data.string("rootFile"))
                .replace(data.string("replaceFrom"), data.string("replaceWith")),
        )
        check(errors(child).none { it.code == data.string("diagnosticCode") })
        check(version(child) == version)
        // the root's own report for its edited text
        check(diagnostics(root) { true }.none { it.code == data.string("diagnosticCode") })
        replace(root, fixture(data.string("rootFile")))
        clean(child)
    }
    case("X341") { data ->
        val root = project()
        val member = data.string("memberFile")
        val path = directory.resolve(member).toString()
        val broken = fixture(root.file).replace(data.string("replaceFrom"), data.string("replaceWith"))
        val problems = with(driver) { utility(ProblemFilesProbe::class) }
        val ide = with(driver) { singleProject() }

        fun described() =
            "problem file ${problems.problem(ide, path)}, errors LSP4IJ holds ${problems.closedErrors(ide, path)}, " +
                "Project Errors ${problems.projectErrorSources(ide, path)}"

        // Project Errors lists each of the member's errors once: no two at the same place with the same text
        fun listedOnce(listed: List<String>) = listed.isNotEmpty() && listed.distinct() == listed

        // with errors, Project Errors lists those of the report that LSP4IJ holds for the closed member, each once
        fun closed(errors: Boolean) =
            awaitUi(
                "closed $id/$member ${if (errors) "has errors and is" else "has none and is no"} problem file, " +
                    "its errors ${if (errors) "listed once" else "not listed"} in Project Errors",
                HANG_LIMIT,
                errorMessage = { "Closed $id/$member: ${described()}" },
            ) {
                val held = problems.closedErrors(ide, path)
                val listed = problems.projectErrors(ide, path)
                problems.problem(ide, path) == errors &&
                    (held > 0) == errors &&
                    if (errors) listedOnce(listed) && listed.size == held else listed.isEmpty()
            }

        // the member is not open, and the root's edit breaks it: its errors are in Project Errors without a Build
        replace(root, broken)
        closed(errors = true)
        // a Build reports them too, in place of the report's, and Project Errors still lists each once. A Build compiles
        // the configured source modules only, so the root's module is configured for it: the Build of the broken root
        // fails once the server has it
        configure(listOf(SharedScenarios.SourceModule(data.string("module"), uri(root.file), emptyList())))
        embedded { execution, project ->
            val text =
                awaitUi(
                    "the Build of the broken $id/${root.file} fails, once its module is configured",
                    HANG_LIMIT,
                    errorMessage = { build -> "The Build of the broken root: $build" },
                    getter = { JsonParser.parseString(execution.build(project, HANG_SECONDS.toInt())).asJsonObject },
                    checker = { it["errors"].asBoolean },
                ).string("text")
            check(text.lines().any { it.startsWith("${member.substringAfterLast('/')}:") && ": error: " in it }) {
                "The Build reports no error in $member: $text"
            }
        }
        // the Build window hands each of its events to IntelliJ's listeners before the Build ends, so a copy that
        // IntelliJ makes of a Build's error, as 2026.3 does of a build view's file message, is in Project Errors now, and
        // stays until the next Build: only the provider's are listed
        val built = problems.projectErrorSources(ide, path)
        check(
            built.all {
                it.endsWith(" [ProjectErrors]")
            },
        ) { "After the Build, Project Errors lists $member's errors from another source: $built" }
        awaitUi("$id/$member's errors are listed once in Project Errors after a Build", HANG_LIMIT, errorMessage = {
            "After the Build, $id/$member: ${described()}"
        }) { listedOnce(problems.projectErrors(ide, path)) }
        println("[playbook] $id: Project Errors in $member after the Build: ${problems.projectErrorSources(ide, path)}")
        // the failed Build opened the member at its first error, as IntelliJ does; closed again, it lists them once still
        closeEditor(open(member))
        closed(errors = true)
        // the undo's report clears them, the Build's included
        replace(root, fixture(root.file))
        closed(errors = false)
        // the rest of the case has the discovered module it started with
        configure(emptyList())
        // opened while broken, it has the errors pulled for it, only those, and none once the root is restored
        replace(root, broken)
        closed(errors = true)
        val child = open(member)
        diagnostics(child) { items -> items.any { it.severity == "Error" } }
        replace(root, fixture(root.file))
        clean(child)
        awaitUi("open $id/$member is no problem file, with no error in Project Errors", HANG_LIMIT, errorMessage = {
            "Open $id/$member: ${described()}"
        }) { !problems.problem(ide, path) && problems.projectErrors(ide, path).isEmpty() }
        // broken again and closed, it is a problem file with its errors again, and the undo clears it
        replace(root, broken)
        errors(child)
        closeEditor(child)
        closed(errors = true)
        replace(root, fixture(root.file))
        closed(errors = false)
    }
    case("X24") { data ->
        val root = project()
        val (child, at) =
            marked(
                data.string("memberFile"),
                fixture(data.string("memberFile"))
                    .replace(data.string("method"), data.string("incompleteMethod")),
            )
        data.strings("types").forEach { type ->
            val value =
                data.string(
                    if (type == data.string("integerType")) "integerValue" else "stringValue",
                )
            replace(
                root,
                fixture(root.file)
                    .replace(
                        data.string("replaceFrom"),
                        SharedScenarios.text(data.string("replaceWith"), type, value),
                    ),
            )
            with(driver) {
                lookup(child.editor, at) { hasType(it, data.string("label"), type) }
                invokeAction("EditorEscape", component = child.editor.component)
            }
        }
        check(Files.readString(directory.resolve(root.file)) == fixture(root.file))
    }
    case("X25") { data ->
        val root = project()
        val file = data.string("file")
        virtual(file, data.string("text"), data.int("version")) {
            with(driver) {
                awaitUi("virtual member participates in current type hierarchy", HANG_LIMIT) {
                    edges(hierarchy(root, root.at(data.string("anchor"))), "subtypes").any {
                        it.string("name") == data.string("name")
                    }
                }
            }
            check(!Files.exists(directory.resolve(file)))
            published(file) { it.isEmpty() }
        }
    }
    case("X26") { data ->
        val root = project()
        val child = open(data.string("file"))
        replace(
            child,
            fixture(child.file).replace(data.string("anchor"), data.string("replaceWith")),
        )
        errors(child)
        discard(child)
        settle(root)
        published(child.file) { it.isEmpty() }
        val reopened = open(child.file)
        check(reopened.text == fixture(child.file))
        check(
            targets(reopened, "definition", reopened.at(data.string("anchor"))).size ==
                data.int("expected"),
        )
    }
    case("X27") { data ->
        project()
        val file = data.string("file")
        write(file, data.string("text"))
        published(file) { it.isNotEmpty() }
        delete(file)
        published(file) { it.isEmpty() }
    }
    case("X28") { data ->
        project()
        val child = open(data.string("file"))
        val old = hierarchy(child, child.at(data.string("anchor")))
        replace(child, data.string("replaceWith"))
        clean(child)
        check(edges(old, "supertypes").isEmpty())
        check(
            edges(
                hierarchy(child, child.at(data.string("anchor"))),
                "supertypes",
            ).none { it.string("name").startsWith(data.string("contains")) },
        )
        replace(child, fixture(child.file))
        check(
            edges(
                hierarchy(child, child.at(data.string("anchor"))),
                "supertypes",
            ).any { it.string("name").startsWith(data.string("contains")) },
        )
    }
    case("X29") { data ->
        val (document, at) = editing(data.string("incompleteBody"))
        val initial = document.text
        val pending =
            (0 until 10).map { index ->
                replace(
                    document,
                    initial.replace(
                        data.string("integerCall"),
                        data.string(
                            if (index % 2 == 1) {
                                "integerCall"
                            } else {
                                "stringCall"
                            },
                        ),
                    ),
                    settle = false,
                )
                protocol.request("textDocument/signatureHelp", document.params(at))
            }
        pending.forEach { awaitRetired("textDocument/signatureHelp", it) }
        val (final, _) = editing(data.string("completedBody"))
        clean(final)
        with(driver) {
            signature(final.editor, final.at(data.string("completedCall"), data.int("offset"))) {
                data.pattern("pattern").containsMatchIn(it.firstOrNull()?.label.orEmpty())
            }
        }
        val root = project()
        val child = open(data.string("memberFile"))
        (0 until 6).forEach { index ->
            replace(
                root,
                fixture(root.file)
                    .replace(
                        data.string("replaceFrom"),
                        SharedScenarios.text(data.string("replaceWith"), index.toString()),
                    ),
                settle = false,
            )
        }
        settle(root)
        settle(child)
        clean(child)
        check(
            targets(child, "definition", child.at(data.string("anchor"))).size ==
                data.int("expected"),
        )
    }
    case("X30") { data ->
        val (document, at) = editing(data.string("body"))
        val pending = protocol.request("textDocument/completion", document.params(at))
        if (pending.cancel(true)) {
            check(pending.isCancelled())
        } else {
            protocol.await("textDocument/completion", pending)
        }
        discard(document)
        val reopened = open(data.string("file"))
        clean(reopened)
        val server = protocol.server()
        val oldProcess = server.getCurrentProcessId()
        server.restart()
        with(driver) {
            awaitUi("new language server process", HANG_LIMIT) {
                server.getCurrentProcessId()?.let { it != oldProcess } == true
            }
        }
        // A new PID precedes initialization and LSP4IJ's asynchronous document reopening.
        // Use the normal didOpen readiness check before touching the new synchronizer.
        clean(open(data.string("file")))
        val (current, cursor) = editing(data.string("body"))
        with(driver) {
            lookup(current.editor, cursor) {
                it.any { item -> item.getLookupString() == data.string("label") }
            }
            invokeAction("EditorEscape", component = current.editor.component)
        }
    }
    case("X31") { data ->
        val document = open(data.string("file"))
        val capabilities = protocol.capabilities().asJsonObject
        data.strings("unsupportedCapabilities").forEach {
            check(
                capabilities[it] == null ||
                    capabilities[it].isJsonNull ||
                    capabilities[it].toString() == "false",
            ) {
                it
            }
        }
        data.strings("supportedCapabilities").forEach {
            check(
                capabilities[it] != null &&
                    !capabilities[it].isJsonNull &&
                    capabilities[it].toString() != "false",
            ) {
                it
            }
        }
        replace(document, data.string("unformatted"))
        check(
            query(
                "textDocument/formatting",
                document,
                extra =
                    mapOf(
                        "options" to
                            mapOf("tabSize" to data.int("tabSize"), "insertSpaces" to true),
                    ),
            ).rows()
                .isNotEmpty(),
        )
    }
    case("X32") { data ->
        data.strings("bodies").forEach { body ->
            val (document, at) = editing(body)
            val result = query("textDocument/completion", document, at)
            val items =
                if (result.isJsonObject) result.asJsonObject["items"].rows() else result.rows()
            check(items.size == data.int("completionCount")) { "$body: $items" }
        }
    }
}
