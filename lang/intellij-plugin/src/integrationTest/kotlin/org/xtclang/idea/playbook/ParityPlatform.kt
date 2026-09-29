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
}
